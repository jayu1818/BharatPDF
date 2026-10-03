package com.bharatpdf.app

import android.app.*
import android.content.*
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.*
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.documentscanner.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.*

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private val dir by lazy { File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "BharatPDF").apply { mkdirs() } }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { u -> if(u.isNotEmpty()) imageToPdf(u) }
    private val mergePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { u -> if(u.size>=2) merge(u) else toast("Select at least 2 PDFs") }
    private val onePdfPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { u -> u?.let { pendingAction(it) } }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); PDFBoxResourceLoader.init(applicationContext); setContentView(R.layout.activity_main)
        status=findViewById(R.id.statusText)
        findViewById<Button>(R.id.scanButton).setOnClickListener { scan() }
        findViewById<Button>(R.id.imagePdfButton).setOnClickListener { imagePicker.launch("image/*") }
        findViewById<Button>(R.id.mergeButton).setOnClickListener { mergePicker.launch("application/pdf") }
        findViewById<Button>(R.id.splitButton).setOnClickListener { action="split"; onePdfPicker.launch("application/pdf") }
        findViewById<Button>(R.id.compressButton).setOnClickListener { action="compress"; onePdfPicker.launch("application/pdf") }
        findViewById<Button>(R.id.lockButton).setOnClickListener { action="lock"; onePdfPicker.launch("application/pdf") }
        findViewById<Button>(R.id.myPdfsButton).setOnClickListener { manager() }
    }
    private var action="split"

    private fun scan() {
        val o=GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(50)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF).build()
        GmsDocumentScanning.getClient(o).getStartScanIntent(this).addOnSuccessListener {
            try { startIntentSenderForResult(it,1001,null,0,0,0,null) } catch(_:Exception){ toast("Scanner start failed") }
        }.addOnFailureListener { toast("Document scanner unavailable") }
    }
    @Deprecated("Compatibility")
    override fun onActivityResult(r:Int,c:Int,d:Intent?) {
        super.onActivityResult(r,c,d)
        if(r==1001 && c==Activity.RESULT_OK && d!=null) GmsDocumentScanningResult.fromActivityResultIntent(d)?.pdf?.uri?.let{ copyUri(it,"Scan") }
    }
    private fun imageToPdf(us:List<Uri>) {
        val out=File(dir,"Images_${System.currentTimeMillis()}.pdf"); val doc=PdfDocument()
        try {
            us.forEachIndexed { i,u ->
                val bmp=contentResolver.openInputStream(u)?.use{BitmapFactory.decodeStream(it)} ?: return@forEachIndexed
                val inf=PdfDocument.PageInfo.Builder(bmp.width.coerceAtLeast(1),bmp.height.coerceAtLeast(1),i+1).create()
                val p=doc.startPage(inf); p.canvas.drawBitmap(bmp,0f,0f,null); doc.finishPage(p); bmp.recycle()
            }
            FileOutputStream(out).use{doc.writeTo(it)}; saved(out)
        } catch(_:Exception){toast("Could not create PDF")} finally{doc.close()}
    }
    private fun merge(us:List<Uri>) {
        try {
            val out=File(dir,"Merged_${System.currentTimeMillis()}.pdf"); val m=PDFMergerUtility(); m.destinationFileName=out.absolutePath
            us.forEach{ contentResolver.openInputStream(it)?.let{ins->m.addSource(ins)} }; m.mergeDocuments(null); saved(out)
        }catch(_:Exception){toast("Could not merge PDFs")}
    }
    private fun pendingAction(u:Uri){ when(action){"split"->split(u);"compress"->compress(u);"lock"->password(u)}}
    private fun split(u:Uri) {
        try {
            contentResolver.openInputStream(u)?.use{src->
                val d=PDDocument.load(src); for(i in 0 until d.numberOfPages){val one=PDDocument(); one.addPage(d.getPage(i)); val f=File(dir,"Split_${System.currentTimeMillis()}_${i+1}.pdf"); one.save(f); one.close()}
                val n=d.numberOfPages; d.close(); status.text="Created $n PDF files"; toast("PDF split")
            }
        }catch(_:Exception){toast("Could not split PDF")}
    }
    private fun compress(u:Uri) {
        try {
            contentResolver.openInputStream(u)?.use{src-> val d=PDDocument.load(src); val f=File(dir,"Compressed_${System.currentTimeMillis()}.pdf"); d.save(f); d.close(); saved(f)}
        }catch(_:Exception){toast("Could not compress PDF")}
    }
    private fun password(u:Uri) {
        val e=EditText(this); e.hint="Password (4+ characters)"; e.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        AlertDialog.Builder(this).setTitle("Lock PDF").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Lock"){_,_->
            val p=e.text.toString(); if(p.length<4) toast("Use at least 4 characters") else lock(u,p)
        }.show()
    }
    private fun lock(u:Uri,p:String) {
        try {
            contentResolver.openInputStream(u)?.use{src->
                val d=PDDocument.load(src); val ap=AccessPermission(); val pol=StandardProtectionPolicy(p,p,ap); pol.encryptionKeyLength=128
                d.protect(pol); val f=File(dir,"Locked_${System.currentTimeMillis()}.pdf"); d.save(f); d.close(); saved(f)
            }
        }catch(_:Exception){toast("Could not lock PDF")}
    }
    private fun copyUri(u:Uri,prefix:String){try{val f=File(dir,"${prefix}_${System.currentTimeMillis()}.pdf"); contentResolver.openInputStream(u)?.use{a->FileOutputStream(f).use{a.copyTo(it)}};saved(f)}catch(_:Exception){toast("Could not save PDF")}}
    private fun saved(f:File){status.text="Saved: ${f.name}";toast("PDF saved")}
    private fun manager(){
        val fs=dir.listFiles()?.filter{it.extension.equals("pdf",true)}?.sortedByDescending{it.lastModified()}.orEmpty()
        if(fs.isEmpty()){toast("No PDFs yet");return}
        val labels=fs.map{"${it.name}  •  ${it.length()/1024} KB"}.toTypedArray()
        AlertDialog.Builder(this).setTitle("My PDFs").setItems(labels){_,which->fileActions(fs[which])}.setNegativeButton("Close",null).show()
    }
    private fun fileActions(f:File){
        AlertDialog.Builder(this).setTitle(f.name).setItems(arrayOf("Share","Rename","Delete","Open")){_,w->
            when(w){0->share(f);1->rename(f);2->delete(f);3->open(f)}
        }.show()
    }
    private fun share(f:File){
        val uri=androidx.core.content.FileProvider.getUriForFile(this,"$packageName.fileprovider",f)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share PDF"))
    }
    private fun rename(f:File){
        val e=EditText(this);e.setText(f.nameWithoutExtension)
        AlertDialog.Builder(this).setTitle("Rename PDF").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->val n=e.text.toString().trim();if(n.isNotEmpty())f.renameTo(File(dir,"$n.pdf"));manager()}.show()
    }
    private fun delete(f:File){f.delete();toast("Deleted");manager()}
    private fun open(f:File){startActivity(Intent(Intent.ACTION_VIEW).apply{setDataAndType(androidx.core.content.FileProvider.getUriForFile(this@MainActivity,"$packageName.fileprovider",f),"application/pdf");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
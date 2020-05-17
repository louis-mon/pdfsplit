import java.io.{ByteArrayOutputStream, File}
import java.nio.file.{Files, Path, Paths}

import javax.swing.JOptionPane
import org.apache.pdfbox.cos.COSObject
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.PDXObject
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.{DefaultResourceCache, PDDocument, PDPage, PDPageContentStream}
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.tools.imageio.ImageIOUtil

import scala.swing.{Action, BoxPanel, Button, Dimension, FileChooser, FlowPanel, FormattedTextField, Frame, Label, MainFrame, Orientation, Panel, ProgressBar, SimpleSwingApplication, Swing, TextField}
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

object PdfSplit {
  def execute(file: File, getStartPage: => String, getEndPage: => String,
              getDpi: => String = "",
              notifier: (Int, Int) => Unit = (_, _) => ()): Unit = {
    val pdf = PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
    val renderer = new PDFRenderer(pdf)
    val nbTotalPages = pdf.getNumberOfPages
    val dpi = Try(getDpi).map(_.toInt).getOrElse(150)
    val startPage = Try(getStartPage).map(_.toInt - 1).getOrElse(0)
    val endPage = Try(getEndPage).map(_.toInt - 1).getOrElse(nbTotalPages - 1)
    val blockSize = 40
    val tmpDir = Paths.get("parts")
    Try(Files.createDirectory(tmpDir))
    val blocks = 0.until(nbTotalPages).sliding(blockSize, blockSize)
    val filenames = collection.mutable.ArrayBuffer[File]()
    blocks.zipWithIndex.foreach{ case (block, i) =>
      val newDoc = new PDDocument()
      println(s"convert block $i")
      block.foreach(currentPageIndex => {
        val currentPageNumber = currentPageIndex + 1
        println(s"convert page $currentPageNumber")
        notifier(currentPageNumber, nbTotalPages)
        val nbParts = if (currentPageIndex >= startPage && currentPageIndex <= endPage) 0.to(1) else List(0)
        nbParts.foreach(part => {
          val outStream = new ByteArrayOutputStream()
          val sourceImage = renderer.renderImageWithDPI(currentPageIndex, dpi)
          val w = sourceImage.getWidth() / nbParts.size
          val h = sourceImage.getHeight()
          ImageIOUtil.writeImage(sourceImage.getSubimage(w * part, 0, w, h), "png", outStream)

          val scale = 72.0 / dpi
          val outW = (w * scale).toInt
          val outH = (h * scale).toInt
          val newPage = new PDPage(new PDRectangle(outW, outH))
          newDoc.addPage(newPage)
          val stream = new PDPageContentStream(newDoc, newPage)
          val image = PDImageXObject.createFromByteArray(newDoc, outStream.toByteArray, "doc")
          stream.drawImage(image, 0, 0, outW, outH)
          outStream.close()
          stream.close()
        })
      })
      val filename = tmpDir.resolve(s"part-$i.pdf").toFile
      newDoc.save(filename)
      newDoc.close()
      filenames += filename
    }
    val merger = new PDFMergerUtility()
    merger.setDestinationFileName("result.pdf")
    filenames.foreach(filename => merger.addSource(filename))
    merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
    pdf.close()
  }
}

object Main extends App {
  PdfSplit.execute(new File(args(0)), args(1), args(2), args(3))
}

object MainApp extends SimpleSwingApplication {
  override def top: Frame = new MainFrame {
    title = "Pdf split"
    preferredSize = new Dimension(640, 480)

    val fileChooser = new FileChooser(new File("."))

    private def numberInput = new FormattedTextField(java.text.NumberFormat.getNumberInstance()) {
      peer.setColumns(10)
    }

    private val startPageInput = numberInput
    private val endPageInput = numberInput
    private val dpiInput = numberInput

    private val fileLabel = new Label() {
      text = "Pas de fichier selectionné"
    }
    private val progressBar = new ProgressBar() {
      labelPainted = true
    }
    contents = new BoxPanel(Orientation.Vertical) {
      contents += fileLabel
      contents += new Button {
        action = Action("Choisir fichier") {
          if (fileChooser.showOpenDialog(this) == FileChooser.Result.Approve)
            fileLabel.text = fileChooser.selectedFile.toString
        }
      }
      contents += Swing.VStrut(10)
      contents += new Label("Première page = 1")
      contents += Swing.VStrut(3)
      contents += new FlowPanel(new Label("Page de début"), startPageInput)
      contents += Swing.VStrut(3)
      contents += new FlowPanel(new Label("Page de fin"), endPageInput)
      contents += Swing.VStrut(3)
      contents += new FlowPanel(new Label("Résolution"), dpiInput)
      contents += Swing.VStrut(10)
      contents += new FlowPanel(new Button {
        action = Action("Lancer la conversion") {
          Future {
            PdfSplit.execute(
              file = fileChooser.selectedFile,
              getStartPage = startPageInput.text,
              getEndPage = endPageInput.text,
              getDpi = dpiInput.text,
              notifier = (current, total) => {
                progressBar.max = total
                progressBar.value = current
                progressBar.label = s"Page $current / $total"
              })
          } onComplete {
            case Success(_) => progressBar.label = "Terminé"
            case Failure(e) => JOptionPane.showMessageDialog(this.peer, e.toString, "Erreur",
              JOptionPane.ERROR_MESSAGE)
          }
        }
      }, progressBar)
      border = Swing.EmptyBorder(10)
    }
  }
}

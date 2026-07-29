package jp.hakamap.project.application.editing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetIngestorTest {
  static {
    System.setProperty("java.awt.headless", "true");
  }

  @TempDir Path temporaryDirectory;

  @Test
  void convertsSinglePagePdfToManagedPng() throws Exception {
    Path pdf = temporaryDirectory.resolve("資料.pdf");
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage());
      document.save(pdf.toFile());
    }

    AssetIngestor.PreparedAsset prepared =
        new AssetIngestor().prepare(pdf, false, temporaryDirectory.resolve(".hakamap-staging"));

    assertThat(prepared.sourceMediaType()).isEqualTo("application/pdf");
    assertThat(prepared.storedMediaType()).isEqualTo("image/png");
    assertThat(prepared.temporary()).isTrue();
    assertThat(prepared.preparedPath()).isRegularFile();
  }

  @Test
  void rejectsMultiPagePdf() throws Exception {
    Path pdf = temporaryDirectory.resolve("複数ページ.pdf");
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage());
      document.addPage(new PDPage());
      document.save(pdf.toFile());
    }

    assertThatThrownBy(
            () ->
                new AssetIngestor()
                    .prepare(pdf, false, temporaryDirectory.resolve(".hakamap-staging")))
        .isInstanceOf(EditingApiException.class)
        .hasMessage("asset-format-unsupported");
  }
}

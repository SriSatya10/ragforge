package com.ragforge.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Parses files from disk into raw text.
 * Supports PDF (via PDFBox) and standard TXT.
 */
@Component
public class DocumentParser {

    public String parse(Path filePath, String filename) throws IOException {
        String lowerName = filename.toLowerCase();
        
        if (lowerName.endsWith(".pdf")) {
            return parsePdf(filePath);
        } else if (lowerName.endsWith(".txt")) {
            return Files.readString(filePath);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + filename);
        }
    }

    private String parsePdf(Path filePath) throws IOException {
        File file = filePath.toFile();
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            // Basic text extraction — could be enhanced with OCR later if needed
            return stripper.getText(document);
        }
    }
}

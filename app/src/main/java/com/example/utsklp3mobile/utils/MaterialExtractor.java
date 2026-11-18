package com.example.utsklp3mobile.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Xml;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Membaca file materi yang dipilih pengguna dan mengubahnya jadi teks.
 * - .pptx : teks tiap slide diekstrak lokal (ZIP + XML).
 * - .pdf  : teks diekstrak dengan pdfbox-android (PDF hasil scan/gambar tidak punya teks).
 */
public final class MaterialExtractor {

    private static final int MAX_PDF_BYTES = 25 * 1024 * 1024;
    // Dijaga pendek karena paket gratis Groq dibatasi sekitar 8.000 token per menit.
    private static final int MAX_TEXT_CHARS = 10000;
    private static final int MAX_PDF_PAGES = 40;
    private static final Pattern SLIDE_NAME = Pattern.compile("ppt/slides/slide(\\d+)\\.xml");

    public static class Material {
        public final String fileName;
        public final String text;

        Material(String fileName, String text) {
            this.fileName = fileName;
            this.text = text;
        }
    }

    private MaterialExtractor() {}

    public static String getFileName(ContentResolver cr, Uri uri) {
        String name = null;
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) {
        }
        if (name == null || name.isEmpty()) {
            String last = uri.getLastPathSegment();
            name = last != null ? last : "materi";
        }
        return name;
    }

    public static Material read(Context context, Uri uri) throws IOException {
        ContentResolver cr = context.getContentResolver();
        String name = getFileName(cr, uri);
        String lower = name.toLowerCase(Locale.ROOT);
        String mime = cr.getType(uri);
        mime = mime == null ? "" : mime.toLowerCase(Locale.ROOT);

        boolean isPdf = lower.endsWith(".pdf") || mime.contains("pdf");
        boolean isPptx = lower.endsWith(".pptx") || mime.contains("presentationml");

        if (lower.endsWith(".ppt") && !isPptx) {
            throw new IOException("Format .ppt lama belum didukung. Simpan ulang sebagai .pptx atau PDF.");
        }
        if (!isPdf && !isPptx) {
            throw new IOException("Format file tidak didukung. Pilih file PDF atau PPTX.");
        }

        byte[] data;
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) throw new IOException("File tidak bisa dibuka.");
            data = readAll(in, isPdf ? MAX_PDF_BYTES : 40 * 1024 * 1024);
        }

        String text = isPdf ? extractPdfText(context, data) : extractPptxText(data);
        text = text.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        if (text.length() < 30) {
            throw new IOException(isPdf
                    ? "Tidak ada teks yang bisa dibaca dari PDF ini (mungkin hasil scan/gambar). Pilih PDF yang teksnya bisa diseleksi."
                    : "Tidak ada teks yang bisa dibaca dari PPTX ini (mungkin hanya berisi gambar).");
        }
        if (text.length() > MAX_TEXT_CHARS) text = text.substring(0, MAX_TEXT_CHARS);
        return new Material(name, text);
    }

    private static byte[] readAll(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > limit) {
                throw new IOException("Ukuran file terlalu besar (maksimal " + (limit / (1024 * 1024)) + " MB).");
            }
        }
        return out.toByteArray();
    }

    private static String extractPdfText(Context context, byte[] pdf) throws IOException {
        try {
            PDFBoxResourceLoader.init(context.getApplicationContext());
            try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(Math.min(doc.getNumberOfPages(), MAX_PDF_PAGES));
                return stripper.getText(doc);
            }
        } catch (IOException e) {
            throw new IOException("PDF tidak bisa dibaca (rusak atau diproteksi password).");
        } catch (Exception e) {
            throw new IOException("Gagal membaca PDF: " + e.getMessage());
        }
    }

    private static String extractPptxText(byte[] pptx) throws IOException {
        Map<Integer, String> slides = new TreeMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(pptx))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Matcher m = SLIDE_NAME.matcher(entry.getName());
                if (!m.matches()) continue;
                byte[] xml = readAll(zis, 5 * 1024 * 1024);
                slides.put(Integer.parseInt(m.group(1)), slideText(xml));
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("File PPTX tidak valid atau rusak.");
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : slides.entrySet()) {
            if (e.getValue().trim().isEmpty()) continue;
            sb.append("Slide ").append(e.getKey()).append(":\n").append(e.getValue()).append("\n\n");
        }
        return sb.toString();
    }

    private static String slideText(byte[] xml) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        StringBuilder sb = new StringBuilder();
        boolean inText = false;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && "a:t".equals(parser.getName())) {
                inText = true;
            } else if (event == XmlPullParser.TEXT && inText) {
                sb.append(parser.getText());
            } else if (event == XmlPullParser.END_TAG) {
                if ("a:t".equals(parser.getName())) inText = false;
                else if ("a:p".equals(parser.getName())) sb.append('\n');
            }
            event = parser.next();
        }
        return sb.toString();
    }
}

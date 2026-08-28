package com.edsonpedreiro.controle;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ignored) {}
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try { startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST); }
                catch (ActivityNotFoundException e) {
                    filePathCallback = null;
                    Toast.makeText(MainActivity.this, "Não foi possível abrir o seletor de arquivos.", Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    if (printManager == null) {
                        Toast.makeText(MainActivity.this, "Serviço de impressão indisponível.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String jobName = "Edson Pedreiro - Controle de Serviços";
                    PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(jobName);
                    PrintAttributes attributes = new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build();
                    printManager.print(jobName, adapter, attributes);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Não foi possível abrir a impressão.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void shareHtml(String html) {
            runOnUiThread(() -> {
                try {
                    File dir = new File(getCacheDir(), "shared");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, "controle-valores-cliente.html");
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(html.getBytes(StandardCharsets.UTF_8));
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this,
                            getPackageName() + ".fileprovider", file);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/html");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.putExtra(Intent.EXTRA_TEXT, "Segue o controle de valores do serviço.");
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send, "Enviar controle ao cliente"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Não foi possível compartilhar o controle.", Toast.LENGTH_LONG).show();
                }
            });
        }


        @JavascriptInterface
        public String saveReceipt(String dataUrl, String fileName, String mimeType) {
            try {
                int comma = dataUrl.indexOf(',');
                if (comma < 0) return "";
                String meta = dataUrl.substring(0, comma);
                String body = dataUrl.substring(comma + 1);
                byte[] bytes = meta.contains(";base64")
                        ? Base64.decode(body, Base64.DEFAULT)
                        : Uri.decode(body).getBytes(StandardCharsets.UTF_8);

                String ext = "";
                if (fileName != null && fileName.contains(".")) {
                    ext = fileName.substring(fileName.lastIndexOf('.')).replaceAll("[^a-zA-Z0-9.]", "");
                }
                String id = "receipt_" + System.currentTimeMillis() + ext;
                File dir = new File(getFilesDir(), "receipts");
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, id);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(bytes);
                }
                return id;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public String readStoredReceipt(String id) {
            try {
                File file = new File(new File(getFilesDir(), "receipts"), id);
                if (!file.exists()) return "";
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = fis.read(buffer)) > 0) bos.write(buffer, 0, n);
                }
                String mime = "application/octet-stream";
                String lower = id.toLowerCase();
                if (lower.endsWith(".pdf")) mime = "application/pdf";
                else if (lower.endsWith(".png")) mime = "image/png";
                else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) mime = "image/jpeg";
                return "data:" + mime + ";base64," + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void openStoredReceipt(String id, String fileName, String mimeType) {
            runOnUiThread(() -> {
                try {
                    File file = new File(new File(getFilesDir(), "receipts"), id);
                    if (!file.exists()) {
                        Toast.makeText(MainActivity.this, "Comprovante não encontrado.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this,
                            getPackageName() + ".fileprovider", file);
                    Intent view = new Intent(Intent.ACTION_VIEW);
                    view.setDataAndType(uri, mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType);
                    view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(view, "Abrir comprovante"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Não foi possível abrir o comprovante.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void openDataUrl(String dataUrl, String fileName, String mimeType) {
            runOnUiThread(() -> {
                try {
                    int comma = dataUrl.indexOf(',');
                    if (comma < 0) throw new IllegalArgumentException("Data URL inválida");
                    String meta = dataUrl.substring(0, comma);
                    String body = dataUrl.substring(comma + 1);
                    byte[] bytes = meta.contains(";base64") ? Base64.decode(body, Base64.DEFAULT)
                            : Uri.decode(body).getBytes(StandardCharsets.UTF_8);
                    String safe = fileName == null || fileName.trim().isEmpty() ? "comprovante" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
                    File dir = new File(getCacheDir(), "shared");
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, safe);
                    try (FileOutputStream fos = new FileOutputStream(file)) { fos.write(bytes); }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", file);
                    Intent view = new Intent(Intent.ACTION_VIEW);
                    view.setDataAndType(uri, mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType);
                    view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(view);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Não foi possível abrir o comprovante.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (filePathCallback == null) return;
            Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}

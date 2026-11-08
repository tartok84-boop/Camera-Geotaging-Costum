package id.desa.kamerageotag;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

@CapacitorPlugin(
    name = "GallerySaver",
    permissions = {
        @Permission(strings = { Manifest.permission.WRITE_EXTERNAL_STORAGE }, alias = "storage")
    }
)
public class GallerySaverPlugin extends Plugin {

    private ByteArrayOutputStream buffer;

    // Foto dikirim bertahap (potongan base64). Potongan terakhir (last=true) memicu penyimpanan ke galeri.
    @PluginMethod
    public void savePhoto(PluginCall call) {
        String data = call.getString("data");
        if (data == null) {
            call.reject("Data foto kosong");
            return;
        }
        boolean first = call.getBoolean("first", true);
        boolean last = call.getBoolean("last", true);
        try {
            byte[] chunk = Base64.decode(data, Base64.DEFAULT);
            if (first || buffer == null) {
                buffer = new ByteArrayOutputStream();
            }
            buffer.write(chunk);
        } catch (Exception e) {
            buffer = null;
            call.reject("Gagal membaca data foto: " + e);
            return;
        }
        if (!last) {
            call.resolve();
            return;
        }
        // Android 9 ke bawah butuh izin penyimpanan; Android 10+ tidak butuh izin untuk menyimpan foto sendiri
        if (Build.VERSION.SDK_INT < 29 && getPermissionState("storage") != PermissionState.GRANTED) {
            requestPermissionForAlias("storage", call, "storagePermsCallback");
            return;
        }
        store(call);
    }

    @PermissionCallback
    private void storagePermsCallback(PluginCall call) {
        if (getPermissionState("storage") == PermissionState.GRANTED) {
            store(call);
        } else {
            buffer = null;
            call.reject("Izin penyimpanan ditolak");
        }
    }

    private void store(PluginCall call) {
        String name = call.getString("fileName", "GEO_" + System.currentTimeMillis() + ".jpg");
        try {
            byte[] bytes = buffer.toByteArray();
            buffer = null;
            JSObject res = new JSObject();
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver resolver = getContext().getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                v.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Kamera Geotag");
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                if (uri == null) {
                    call.reject("Gagal membuat berkas di galeri");
                    return;
                }
                OutputStream os = resolver.openOutputStream(uri);
                if (os == null) {
                    call.reject("Gagal membuka berkas galeri");
                    return;
                }
                os.write(bytes);
                os.flush();
                os.close();
                res.put("uri", uri.toString());
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Kamera Geotag");
                if (!dir.exists() && !dir.mkdirs()) {
                    call.reject("Gagal membuat folder galeri");
                    return;
                }
                File f = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(bytes);
                fos.flush();
                fos.close();
                MediaScannerConnection.scanFile(getContext(), new String[] { f.getAbsolutePath() }, new String[] { "image/jpeg" }, null);
                res.put("uri", f.getAbsolutePath());
            }
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Gagal menyimpan: " + e);
        }
    }
}

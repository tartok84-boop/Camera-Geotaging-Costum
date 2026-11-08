package id.desa.kamerageotag;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.OutputStream;

@CapacitorPlugin(name = "GallerySaver")
public class GallerySaverPlugin extends Plugin {

    @PluginMethod
    public void savePhoto(PluginCall call) {
        String data = call.getString("data");
        String name = call.getString("fileName", "GEO_" + System.currentTimeMillis() + ".jpg");
        if (data == null) {
            call.reject("Data foto kosong");
            return;
        }
        try {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            ContentResolver resolver = getContext().getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= 29) {
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Kamera Geotag");
                v.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            Uri uri = null;
            try {
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            } catch (Exception first) {
                v.remove(MediaStore.Images.Media.RELATIVE_PATH);
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            }
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
            os.close();
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                resolver.update(uri, done, null, null);
            }
            JSObject res = new JSObject();
            res.put("uri", uri.toString());
            call.resolve(res);
        } catch (Exception e) {
            call.reject("Gagal menyimpan: " + e.toString());
        }
    }
}

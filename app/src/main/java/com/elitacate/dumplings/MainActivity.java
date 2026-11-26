package com.elitacate.dumplings;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // El Itacate VdC, C. Ote. 31 #367, Providencia, Xico
    private static final GeoPoint DESTINO = new GeoPoint(19.2841718, -98.9228052);

    private MapView mapa;
    private TextView info, eta;
    private FusedLocationProviderClient fused;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String[]> pedirPermisos =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), r -> {
                if (r.containsValue(true)) obtenerUbicacion();
                else info.setText("Necesitamos el permiso de ubicación para calcular la distancia.");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // osmdroid exige identificar la app para poder descargar el mapa
        Configuration.getInstance().setUserAgentValue(getPackageName());
        setContentView(R.layout.activity_main);

        mapa = findViewById(R.id.mapa);
        info = findViewById(R.id.info);
        eta = findViewById(R.id.eta);
        fused = LocationServices.getFusedLocationProviderClient(this);

        mapa.setTileSource(TileSourceFactory.MAPNIK);
        mapa.setMultiTouchControls(true);
        mapa.getController().setZoom(16.0);
        mapa.getController().setCenter(DESTINO);
        mapa.getOverlays().add(crearPin(DESTINO, "El Itacate VdC"));

        Button boton = findViewById(R.id.btnDistancia);
        boton.setOnClickListener(v -> {
            if (tienePermiso()) obtenerUbicacion();
            else pedirPermisos.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        });
        boton.performClick(); // pide ubicación y calcula el tiempo al abrir
    }

    private boolean tienePermiso() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void obtenerUbicacion() {
        info.setText("Obteniendo tu ubicación…");
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(loc -> {
                    if (loc == null) {
                        info.setText("Activa el GPS e inténtalo de nuevo.");
                        return;
                    }
                    trazarRuta(new GeoPoint(loc.getLatitude(), loc.getLongitude()));
                })
                .addOnFailureListener(e -> info.setText("No se pudo obtener tu ubicación."));
    }

    private void trazarRuta(GeoPoint yo) {
        final double recta = yo.distanceToAsDouble(DESTINO) / 1000.0;
        hilo.execute(() -> {
            try {
                String url = "https://router.project-osrm.org/route/v1/driving/"
                        + yo.getLongitude() + "," + yo.getLatitude() + ";"
                        + DESTINO.getLongitude() + "," + DESTINO.getLatitude()
                        + "?overview=full&geometries=geojson";
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                    String linea;
                    while ((linea = r.readLine()) != null) sb.append(linea);
                }
                JSONObject ruta = new JSONObject(sb.toString()).getJSONArray("routes").getJSONObject(0);
                JSONArray coords = ruta.getJSONObject("geometry").getJSONArray("coordinates");
                List<GeoPoint> puntos = new ArrayList<>();
                for (int i = 0; i < coords.length(); i++) {
                    JSONArray p = coords.getJSONArray(i);
                    puntos.add(new GeoPoint(p.getDouble(1), p.getDouble(0)));
                }
                final double km = ruta.getDouble("distance") / 1000.0;
                final long min = Math.round(ruta.getDouble("duration") / 60.0);
                ui.post(() -> {
                    eta.setText(min + " min");
                    dibujar(yo, puntos, String.format(Locale.getDefault(), "%.1f km en auto hasta El Itacate VdC", km));
                });
            } catch (Exception e) {
                // Sin internet o sin ruta: línea recta
                List<GeoPoint> linea = new ArrayList<>();
                linea.add(yo);
                linea.add(DESTINO);
                ui.post(() -> {
                    eta.setText("—");
                    dibujar(yo, linea, String.format(Locale.getDefault(), "Estás a unos %.1f km en línea recta.", recta));
                });
            }
        });
    }

    private void dibujar(GeoPoint yo, List<GeoPoint> puntos, String texto) {
        mapa.getOverlays().clear();
        Polyline linea = new Polyline();
        linea.setPoints(puntos);
        linea.getOutlinePaint().setColor(Color.parseColor("#FF5A00"));
        linea.getOutlinePaint().setStrokeWidth(12f);
        mapa.getOverlays().add(linea);
        mapa.getOverlays().add(crearPin(DESTINO, "El Itacate VdC"));
        mapa.getOverlays().add(crearPin(yo, "Tú estás aquí"));
        mapa.zoomToBoundingBox(BoundingBox.fromGeoPoints(puntos), true, 120);
        mapa.invalidate();
        info.setText(texto);
    }

    private Marker crearPin(GeoPoint punto, String titulo) {
        Marker m = new Marker(mapa);
        m.setPosition(punto);
        m.setTitle(titulo);
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        return m;
    }

    @Override protected void onResume() { super.onResume(); mapa.onResume(); }
    @Override protected void onPause() { super.onPause(); mapa.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); hilo.shutdown(); }
}

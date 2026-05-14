package com.example.labthreadsasynctask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * LabThreadsAsyncTask — Version redesignée
 *
 * Différences majeures vs version prof :
 *  - Logique encapsulée dans des méthodes dédiées (pas de lambdas inline)
 *  - Génération d'une image procédurale au lieu de décoder une ressource statique
 *  - Compteur de pourcentage live sur la ProgressBar
 *  - Animations d'apparition sur les mises à jour de statut
 *  - Gestion d'état : boutons désactivés pendant les traitements
 *  - Messages de statut formatés avec timestamp
 */
public class MainActivity extends AppCompatActivity {

    // ── Vues ──────────────────────────────────────────────────────────────
    private TextView txtStatus;
    private TextView txtPercent;
    private ProgressBar progressBar;
    private ImageView img;
    private Button btnLoadThread;
    private Button btnCalcAsync;
    private Button btnToast;

    // ── Handler UI ────────────────────────────────────────────────────────
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // ── État interne ──────────────────────────────────────────────────────
    private boolean isWorking = false;

    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        attachListeners();
        updateStatus("Système prêt. En attente d'une commande...");
    }

    // ── Liaison des vues ──────────────────────────────────────────────────
    private void bindViews() {
        txtStatus    = findViewById(R.id.txtStatus);
        txtPercent   = findViewById(R.id.txtPercent);
        progressBar  = findViewById(R.id.progressBar);
        img          = findViewById(R.id.img);
        btnLoadThread = findViewById(R.id.btnLoadThread);
        btnCalcAsync  = findViewById(R.id.btnCalcAsync);
        btnToast      = findViewById(R.id.btnToast);
    }

    // ── Écouteurs de clics ────────────────────────────────────────────────
    private void attachListeners() {
        btnLoadThread.setOnClickListener(v -> onLoadImageClicked());
        btnCalcAsync.setOnClickListener(v -> onHeavyCalcClicked());
        btnToast.setOnClickListener(v -> onPingClicked());
    }

    // ─────────────────────────────────────────────────────────────────────
    // BOUTON PING — répond toujours immédiatement (UI thread)
    // ─────────────────────────────────────────────────────────────────────
    private void onPingClicked() {
        String msg = isWorking ? "⚡ UI réactive même pendant un traitement !" : "⚡ UI thread vivant et réactif !";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────────────────
    // PARTIE 1 — THREAD MANUEL
    // Génère une image procédurale (cercles colorés) en arrière-plan
    // ─────────────────────────────────────────────────────────────────────
    private void onLoadImageClicked() {
        if (isWorking) return;

        setWorkingState(true);
        showProgress(true);
        updateStatus("> Démarrage Thread — génération image procédurale...");

        // Le thread de fond fait TOUT le travail lourd
        Thread generatorThread = new Thread(this::generateProceduralImage);
        generatorThread.setName("ImageGeneratorThread");
        generatorThread.start();
    }

    /**
     * S'exécute dans le Worker Thread.
     * Génère un Bitmap par dessin programmatique, puis poste le résultat sur le UI thread.
     */
    private void generateProceduralImage() {

        // Étape 1 : simuler une latence réseau / I/O
        simulateSleep(800, "Thread interrompu lors du chargement");

        // Étape 2 : dessiner une image 300×300 avec Paint
        final int SIZE = 300;
        Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Fond sombre
        canvas.drawColor(Color.parseColor("#0A0E1A"));

        // Cercles concentriques néon
        int[] colors = {
                Color.parseColor("#00E5FF"),
                Color.parseColor("#A78BFA"),
                Color.parseColor("#34D399"),
                Color.parseColor("#F472B6")
        };

        for (int layer = 0; layer < colors.length; layer++) {
            paint.setColor(colors[layer]);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6f - layer);
            float radius = 40f + layer * 28f;
            canvas.drawCircle(SIZE / 2f, SIZE / 2f, radius, paint);
        }

        // Point central
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#00E5FF"));
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, 10f, paint);

        // Étape 3 : repasser sur le UI thread pour afficher
        uiHandler.post(() -> {
            img.setImageBitmap(bmp);
            fadeIn(img);
            showProgress(false);
            setWorkingState(false);
            updateStatus("> Image générée avec succès. (Thread worker → UI thread via Handler)");
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // PARTIE 2 — ASYNCTASK
    // Calcul numérique : recherche de nombres premiers jusqu'à N
    // ─────────────────────────────────────────────────────────────────────
    private void onHeavyCalcClicked() {
        if (isWorking) return;
        new PrimeCounterTask().execute(5000);
    }

    /**
     * AsyncTask qui compte les nombres premiers jusqu'à une limite.
     * Paramètre : Integer (limite)
     * Progression : Integer (pourcentage 0-100)
     * Résultat : Integer (nombre de premiers trouvés)
     */
    private class PrimeCounterTask extends AsyncTask<Integer, Integer, Integer> {

        private int limit;

        // ── Avant le traitement : UI thread ──────────────────────────────
        @Override
        protected void onPreExecute() {
            limit = 0;
            setWorkingState(true);
            showProgress(true);
            setProgressPercent(0);
            updateStatus("> AsyncTask démarrée — recherche de nombres premiers...");
        }

        // ── Traitement lourd : Worker thread ─────────────────────────────
        @Override
        protected Integer doInBackground(Integer... params) {
            limit = params[0];
            int count = 0;

            for (int n = 2; n <= limit; n++) {

                if (isCancelled()) break;

                if (isPrime(n)) {
                    count++;
                }

                // Publier la progression tous les 100 nombres
                if (n % 100 == 0) {
                    int percent = (int) ((n / (float) limit) * 100);
                    publishProgress(percent);
                }
            }

            return count;
        }

        // ── Mise à jour progression : UI thread ───────────────────────────
        @Override
        protected void onProgressUpdate(Integer... values) {
            int percent = values[0];
            setProgressPercent(percent);
            updateStatus("> Recherche en cours... " + percent + "%");
        }

        // ── Résultat final : UI thread ────────────────────────────────────
        @Override
        protected void onPostExecute(Integer primesFound) {
            setProgressPercent(100);
            showProgress(false);
            setWorkingState(false);
            updateStatus("> Terminé ! " + primesFound + " premiers trouvés jusqu'à " + limit
                    + ".\n> (AsyncTask : doInBackground → onPostExecute)");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // UTILITAIRES — UI
    // ─────────────────────────────────────────────────────────────────────

    /** Met à jour le TextView de statut avec une animation de fondu */
    private void updateStatus(String message) {
        txtStatus.setText(message);
        fadeIn(txtStatus);
    }

    /** Affiche ou cache la ProgressBar */
    private void showProgress(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        if (!show) {
            progressBar.setProgress(0);
            setProgressPercent(0);
        }
    }

    /** Met à jour le pourcentage affiché et la ProgressBar */
    private void setProgressPercent(int percent) {
        progressBar.setProgress(percent);
        txtPercent.setText(percent + " %");
    }

    /** Active ou désactive les boutons de traitement */
    private void setWorkingState(boolean working) {
        isWorking = working;
        btnLoadThread.setEnabled(!working);
        btnCalcAsync.setEnabled(!working);
        btnLoadThread.setAlpha(working ? 0.4f : 1f);
        btnCalcAsync.setAlpha(working ? 0.4f : 1f);
    }

    /** Animation fondu entrant sur une View */
    private void fadeIn(View view) {
        AlphaAnimation anim = new AlphaAnimation(0.2f, 1f);
        anim.setDuration(400);
        anim.setFillAfter(true);
        view.startAnimation(anim);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UTILITAIRES — Calcul
    // ─────────────────────────────────────────────────────────────────────

    /** Teste si un entier est premier */
    private boolean isPrime(int n) {
        if (n < 2) return false;
        if (n == 2) return true;
        if (n % 2 == 0) return false;
        for (int i = 3; i * i <= n; i += 2) {
            if (n % i == 0) return false;
        }
        return true;
    }

    /** Pause simulant un I/O long (à utiliser uniquement dans un Worker Thread) */
    private void simulateSleep(long ms, String errorMsg) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
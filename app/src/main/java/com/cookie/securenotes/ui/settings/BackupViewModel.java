package com.cookie.securenotes.ui.settings;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cookie.securenotes.R;
import com.cookie.securenotes.manager.backup.ExporterManager;
import com.cookie.securenotes.security.BackupCryptoException;
import com.cookie.securenotes.session.SecureSession;
import com.cookie.securenotes.util.AppExecutors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * ViewModel dedicato al backup. Vive e muore con SettingsActivity (NON sopravvive
 * a una navigazione via da questa schermata — vedi doc 13/14 per il perché e per
 * il percorso futuro con WorkManager, che invece sopravviverebbe).
 *
 * L'Activity si limita a osservare getUiState() e a chiamare avviaExport/avviaImport:
 * tutta la gestione di thread/executor resta qui dentro, non nell'Activity.
 */
public class BackupViewModel extends AndroidViewModel {

    /** Stato dell'operazione corrente, osservato da SettingsActivity per abilitare/disabilitare i pulsanti. */
    public enum Stato { INATTIVO, IN_CORSO, COMPLETATO, ERRORE }

    /** Coppia stato+messaggio pronta per l'Activity: il messaggio è già tradotto/formattato, pronto per un Toast. */
    public static class BackupUiState {
        public Stato stato;
        public String messaggio;
    }

    private final ExporterManager exporterManager;
    private final MutableLiveData<BackupUiState> uiState = new MutableLiveData<>();

    public BackupViewModel(@NonNull Application application) {
        super(application);
        exporterManager = new ExporterManager(SecureSession.getInstance(), application);

        // stato iniziale: nessuna operazione in corso
        BackupUiState iniziale = new BackupUiState();
        iniziale.stato = Stato.INATTIVO;
        uiState.setValue(iniziale);
    }

    public LiveData<BackupUiState> getUiState() {
        return uiState;
    }

    /** true se c'è un export/import in corso — usato per decidere se avvisare prima di uscire dalla schermata. */
    public boolean isOperazioneInCorso() {
        BackupUiState stato = uiState.getValue();
        return stato != null && stato.stato == Stato.IN_CORSO;
    }

    /** Segnala all'ExporterManager di interrompersi appena possibile (cancellazione cooperativa, non immediata). */
    public void annullaOperazione() {
        exporterManager.cancel();
    }

    /** Riporta lo stato a INATTIVO dopo che l'Activity ha già mostrato il messaggio di COMPLETATO/ERRORE. */
    public void resetStato() {
        BackupUiState reset = new BackupUiState();
        reset.stato = Stato.INATTIVO;
        uiState.setValue(reset);
    }

    public void avviaExport(Uri destinazioneUri, char[] password) {
        postStato(Stato.IN_CORSO, null);

        exporterManager.esegui(() -> {
            try (OutputStream out = getApplication().getContentResolver().openOutputStream(destinazioneUri)) {
                if (out == null) {
                    throw new IOException("Impossibile aprire il file di destinazione");
                }

                ExporterManager.BackupResult risultato = exporterManager.exportMedia(password, out, null);
                postStato(Stato.COMPLETATO, formattaEsitoExport(risultato));

            } catch (ExporterManager.OperazioneAnnullataException e) {
                postStato(Stato.ERRORE, getApplication().getString(R.string.msg_backup_cancelled));

            } catch (Exception e) {
                postStato(Stato.ERRORE, getApplication().getString(R.string.error_export_failed, e.getMessage()));

            } finally {
                Arrays.fill(password, '\0'); // la password non serve più, si sovrascrive subito in RAM
            }
        });
    }

    public void avviaImport(Uri sorgenteUri, char[] password) {
        postStato(Stato.IN_CORSO, null);

        exporterManager.esegui(() -> {
            try (InputStream in = getApplication().getContentResolver().openInputStream(sorgenteUri)) {
                if (in == null) {
                    throw new IOException("Impossibile aprire il file di backup scelto");
                }

                ExporterManager.BackupResult risultato = exporterManager.importMedia(password, in, null);
                postStato(Stato.COMPLETATO, formattaEsitoImport(risultato));

            } catch (ExporterManager.OperazioneAnnullataException e) {
                postStato(Stato.ERRORE, getApplication().getString(R.string.msg_backup_cancelled));

            } catch (BackupCryptoException e) {
                // caso specifico: password sbagliata o file corrotto, messaggio dedicato invece di quello generico
                postStato(Stato.ERRORE, getApplication().getString(R.string.error_backup_wrong_password));

            } catch (Exception e) {
                postStato(Stato.ERRORE, getApplication().getString(R.string.error_import_failed, e.getMessage()));

            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private String formattaEsitoExport(ExporterManager.BackupResult r) {
        if (r.saltati.isEmpty()) {
            return getApplication().getString(R.string.msg_export_success_count, r.riusciti);
        }
        return getApplication().getString(R.string.msg_export_success_with_skipped, r.riusciti, r.saltati.size());
    }

    private String formattaEsitoImport(ExporterManager.BackupResult r) {
        if (r.saltati.isEmpty()) {
            return getApplication().getString(R.string.msg_import_success_count, r.riusciti);
        }
        return getApplication().getString(R.string.msg_import_success_with_skipped, r.riusciti, r.saltati.size());
    }

    /** La LiveData va sempre aggiornata dal main thread: qui si fa il passaggio dal thread di ExporterManager. */
    private void postStato(Stato stato, String messaggio) {
        AppExecutors.getInstance().mainThread(() -> {
            BackupUiState nuovo = new BackupUiState();
            nuovo.stato = stato;
            nuovo.messaggio = messaggio;
            uiState.setValue(nuovo);
        });
    }

    /** Chiamato automaticamente da Android quando il ViewModel viene distrutto insieme a SettingsActivity. */
    @Override
    protected void onCleared() {
        super.onCleared();
        exporterManager.cancel();
        exporterManager.shutdown();
    }
}

# CryptoException

## Ruolo e metodi

Eccezione controllata per gli errori crittografici. Il costruttore
`CryptoException(String, Throwable)` conserva un messaggio leggibile e la causa originale.

## Collegamenti

[CryptoManager](CryptoManager.md) la genera; [SecureSession](../session/SecureSession.md) e i
repository la propagano fino ai ViewModel, che mostrano un errore alla UI.

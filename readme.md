├── SecureNotesApplication.java
├── ui/
│   ├── login/          (LoginActivity, LoginViewModel)
│   ├── dashboard/       (DashboardActivity, DashboardViewModel)
│   ├── editor/          (NoteEditorActivity)
│   ├── archive/         (FileArchiveActivity)
│   └── settings/        (SettingsActivity)
├── data/
│   ├── local/
│   │   ├── db/          (Room: AppDatabase, NoteDao, NoteEntity)
│   │   └── prefs/       (SecurePrefsManager)
│   ├── crypto/          (KeystoreManager, FileEncryptionManager)
│   └── repository/      (NoteRepository, FileRepository)
└── util/                (SessionManager, BackupManager)
# ArchivioPagerAdapter

## Ruolo e metodi

Adapter del ViewPager2. Il costruttore riceve l'Activity. createFragment associa posizione 0 a FotoFragment, 1 a VideoFragment e 2 a PdfFragment; rifiuta altre posizioni. getItemCount restituisce 3.

## Collegamenti

ArchivioActivity lo installa nel ViewPager2.

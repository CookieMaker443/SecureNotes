`decodeSampledBitmap` fa un doppio passaggio: prima chiede solo le **dimensioni** dell'immagine (`inJustDecodeBounds = true`, velocissimo, non alloca pixel), calcola quanto "sottocampionare" (`inSampleSize`, potenze di 2: 1, 2, 4, 8...), poi decodifica **davvero** solo alla risoluzione ridotta. Evita di allocare in RAM un'immagine a piena risoluzione (magari 4000x3000px da una fotocamera) solo per mostrarne 200x200px.

```prompt
ok allora, di compilare compila, per favore mi serve adesso che mi creo la documentazione, mi serve che crei anche dioversi file che spiegano per filo e per segno le classi create, come si collegano tra loro e cosa fanno i vari metori e in che modom lo fanno per favore
```




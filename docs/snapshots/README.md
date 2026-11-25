# Πηγές για τον γρίφο του φυλακίου

Το [snapshot του Φάρου Ακρωτηρίου](akrotiri-lighthouse.json) προέρχεται από δύο επιτυχείς κλήσεις στις 25 Νοεμβρίου 2026. Το [#8](https://github.com/PantelisZara/EscapeHub/issues/8) τεκμηριώνει τις πηγές για τη διαδρομή του φυλακίου στο [#22](https://github.com/PantelisZara/EscapeHub/issues/22).

1. Η εγγραφή [Q60750512 του Wikidata](https://www.wikidata.org/w/index.php?title=Q60750512&oldid=1967673235) δίνει τις συντεταγμένες του φάρου από την ιδιότητα `P625`, `36.35779, 25.35706`.
2. Οι συντεταγμένες χρησιμοποιήθηκαν ως `lat` και `lon` στο [reverse endpoint του Nominatim](https://nominatim.org/release-docs/latest/api/Reverse/). Η απόκριση επέστρεψε τον [κόμβο 1446175116 του OpenStreetMap](https://www.openstreetmap.org/node/1446175116) με κατηγορία `man_made`, τύπο `lighthouse` και όνομα «Cape Akrotiri Lighthouse». Οι δύο θέσεις απέχουν περίπου 11,1 μέτρα.

Στον γρίφο, το αρχείο δίνει την εγγραφή του Wikidata και ο χάρτης επιβεβαιώνει ποιο σημείο αντιστοιχεί στον φάρο. Το εύρημα του χάρτη είναι το ζεύγος `36.3577023, 25.3571183`. Οι συντεταγμένες του Wikidata χρησιμοποιούνται για την αναζήτηση. Το demo μπορεί να διαβάζει το αποθηκευμένο snapshot, ώστε τα αποτελέσματα και οι δοκιμές του να είναι σταθερά. Το Nominatim επιστρέφει το πλησιέστερο κατάλληλο χαρτογραφημένο αντικείμενο, επομένως μια μελλοντική λήψη χρειάζεται νέο έλεγχο ονόματος, τύπου και θέσης.

Για χειροκίνητη επαλήθευση, κάλεσε πρώτα το `wikidata.request` του snapshot με περιγραφικό User-Agent όπως `EscapeHub/0.1 (https://github.com/PantelisZara/EscapeHub)`. Πέρασε τις τιμές `P625` στη δεύτερη κλήση σύμφωνα με το `openStreetMap.request`. Η [πολιτική του δημόσιου Nominatim](https://operations.osmfoundation.org/policies/nominatim/) ορίζει μέγιστο ρυθμό ενός αιτήματος ανά δευτερόλεπτο και απαιτεί αναγνωρίσιμο User-Agent και αναφορά προέλευσης.

Τα επιλεγμένα πεδία του Wikidata καλύπτονται από [CC0](https://www.wikidata.org/wiki/Wikidata:Licensing). Τα επιλεγμένα πεδία του χάρτη καλύπτονται από [ODbL 1.0 · © OpenStreetMap contributors](https://www.openstreetmap.org/copyright). Όταν εμφανιστούν τα δεδομένα του χάρτη στο παιχνίδι, η οθόνη του γρίφου θα πρέπει να εμφανίζει την αναφορά προέλευσης με σύνδεσμο προς την άδεια.

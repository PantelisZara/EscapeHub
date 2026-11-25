# EscapeHub — Το Τελευταίο Σήμα

Συνεργατικό point-and-click μυστήριο σε απομονωμένο φάρο.
Εξερευνήστε τρεις διαδρομές, συνδυάστε τα ευρήματα και ανάψτε ξανά το φως.

## Εκτέλεση με Docker

Με εγκατεστημένο Docker, τρέξε το δημόσιο image του demo από το GitHub Container Registry. Η λήψη γίνεται χωρίς σύνδεση στο GitHub.

```sh
docker run --rm --pull always -p 127.0.0.1:8080:8080 ghcr.io/panteliszara/escapehub:latest
```

Εναλλακτικά, από τον φάκελο που περιέχει το `compose.yaml`:

```sh
docker compose up --pull always
```

Άνοιξε `http://127.0.0.1:8080`. Για να δοκιμάσεις δεύτερο παίκτη στον ίδιο υπολογιστή, άνοιξε `http://localhost:8080` και χρησιμοποίησε τον ίδιο κωδικό δωματίου. Τερμάτισε την εκτέλεση με `Ctrl+C`. Το image περιέχει τη Java και τα αρχεία του παιχνιδιού. Το demo λειτουργεί χωρίς βάση δεδομένων και διατηρεί την πρόοδο στη μνήμη του container.

Το CI δημοσιεύει το `latest` από το `main`. Για συγκεκριμένη έκδοση, αντικατάστησε το `latest` με το δημοσιευμένο tag της ή όρισε το αντίστοιχο image μέσω του `ESCAPEHUB_IMAGE` στο Compose. Αν η πόρτα 8080 χρησιμοποιείται ήδη, όρισε άλλη με `ESCAPEHUB_PORT=8081 docker compose up --pull always` και άνοιξε την ίδια πόρτα στον browser.

Το CI δημοσιεύει image για `linux/amd64`. Σε ARM με διαθέσιμη εξομοίωση, πρόσθεσε `--platform linux/amd64` στο `docker run` ή όρισε `DOCKER_DEFAULT_PLATFORM=linux/amd64` πριν το `docker compose up`.

## Ανάπτυξη χωρίς Docker

Java 21. Build, tests και static analysis:

Για linux
```sh
mkdir -p .cache/tmp
MAVEN_USER_HOME="$PWD/.cache/maven-user-home" TMPDIR="$PWD/.cache/tmp" ./mvnw verify
```
Για windows
```sh
mkdir -p .cache/tmp
.\mvnw.cmd verify
```

Τοπικό demo:

```sh
java "-Djava.io.tmpdir=.cache/tmp" -cp target/classes gr.escapehub.web.DemoServer
```

1. Άνοιξε `http://127.0.0.1:8080`, γράψε ένα προσωρινό όνομα και δημιούργησε δωμάτιο.
2. Ο δεύτερος παίκτης ανοίγει `http://localhost:8080` στον ίδιο υπολογιστή ή χρησιμοποιεί ξεχωριστό προφίλ browser. Γράφει το όνομά του και τον κωδικό που εμφανίζεται στη βάρδιά σου.
3. Παίξτε το μηχανοστάσιο. Οι ενέργειες και το επιβεβαιωμένο εύρημα εμφανίζονται και στους δύο. Φυλάκιο και ασύρματος ακολουθούν.

Κάθε δωμάτιο χωρά δύο παίκτες. Η ανανέωση της σελίδας διατηρεί τη συμμετοχή στο ίδιο προφίλ και URL. Τα δωμάτια και η πρόοδος χάνονται με την επανεκκίνηση του τοπικού server.

#### Demo API

- `POST /api/room` δέχεται form με `name` και προαιρετικό `code`. Η δημιουργία επιστρέφει `201`, η συμμετοχή `200` και ένα γεμάτο δωμάτιο `409`. Η συμμετοχή αποθηκεύεται σε HttpOnly cookie.
- `GET /api/engine-room` επιστρέφει την κοινή κατάσταση (`code`, `players`, `revision`, `steps`, `solved`, `power`). Χωρίς συμμετοχή επιστρέφει `401`. Η τιμή `power` παραμένει `null` μέχρι τη λύση.
- `POST /api/engine-room` δέχεται form με `action` και το `revision` που είδε ο παίκτης. Αν έχει αλλάξει η πρόοδος, επιστρέφει `409` μαζί με την τρέχουσα κατάσταση. Τα POST απαιτούν ίδιο origin.

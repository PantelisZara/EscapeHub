# EscapeHub — Το Τελευταίο Σήμα

Συνεργατικό παιχνίδι μυστηρίου για δύο παίκτες σε έναν απομονωμένο φάρο.

## Docker

Τρέξε την έκδοση 0.1.2 σε PowerShell (Docker Desktop με Linux containers), Linux ή macOS.

```sh
docker run --rm -p 127.0.0.1:8080:8080 -v escapehub-accounts:/var/lib/escapehub ghcr.io/panteliszara/escapehub:v0.1.2
```

Εναλλακτικά, από τον φάκελο του repo με το `compose.yaml`:

```sh
docker compose up --pull always
```

## Τοπική εκτέλεση

Απαιτείται Java 21. Από τον φάκελο του repo, τρέξε:

- Windows PowerShell: `New-Item -ItemType Directory -Force .cache/tmp`, μετά `.\mvnw.cmd verify`
- Linux και macOS: `mkdir -p .cache/tmp && ./mvnw verify`

```sh
java -cp target/classes gr.escapehub.web.DemoServer
```

## Παιχνίδι

Άνοιξε το [παιχνίδι](http://127.0.0.1:8080) και δημιούργησε δωμάτιο. Στον ίδιο υπολογιστή, ο δεύτερος παίκτης ανοίγει [ξεχωριστή διεύθυνση](http://localhost:8080) και χρησιμοποιεί τον ίδιο κωδικό δωματίου.

[Ιστορικό αλλαγών](CHANGELOG.md)

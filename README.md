# EscapeHub — Το Τελευταίο Σήμα

Συνεργατικό point-and-click μυστήριο σε απομονωμένο φάρο.
Εξερευνήστε τρεις διαδρομές, συνδυάστε τα ευρήματα και ανάψτε ξανά το φως.

Java 21. Build, tests και static analysis:

```sh
mkdir -p .cache/tmp
MAVEN_USER_HOME="$PWD/.cache/maven-user-home" TMPDIR="$PWD/.cache/tmp" ./mvnw verify
```

Τοπικό demo:

```sh
java -Djava.io.tmpdir=.cache/tmp -cp target/classes gr.escapehub.web.DemoServer
```

Άνοιξε `http://127.0.0.1:8080`. Παίζεται το μηχανοστάσιο· φυλάκιο και ασύρματος ακολουθούν.
Η πρόοδος είναι προσωρινή, ανά browser, και χάνεται όταν κλείσει ο server.

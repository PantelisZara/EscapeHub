const room = document.querySelector('#engine-dialog');
const openRoom = document.querySelector('#open-engine');
const controls = [...document.querySelectorAll('[data-action]')];
const feedback = document.querySelector('#feedback');
const connection = document.querySelector('#connection');
const reconnect = document.querySelector('#reconnect');
const roomReconnect = document.querySelector('#room-reconnect');
const lobby = document.querySelector('#lobby');
const guestForm = document.querySelector('#guest-form');
const guestCode = document.querySelector('#guest-code');
const enterRoom = document.querySelector('#enter-room');
const lobbyFeedback = document.querySelector('#lobby-feedback');
const roomDetails = document.querySelector('#room-details');
const roomCode = document.querySelector('#room-code');
const roomPlayers = document.querySelector('#room-players');
const order = ['open-valve', 'prime-pump', 'start-generator'];
const enginePath = '/api/engine-room';
const archivePath = '/api/archive';
const archive = document.querySelector('#archive-dialog');
const openArchive = document.querySelector('#open-archive');
const closeArchive = document.querySelector('#close-archive');
const archiveMarker = document.querySelector('#archive-marker');
const archiveMap = document.querySelector('#archive-map');
const submitMarker = document.querySelector('#submit-marker');
const archiveReconnect = document.querySelector('#archive-reconnect');
const radioPath = '/api/radio';
const radio = document.querySelector('#radio-dialog');
const openRadio = document.querySelector('#open-radio');
const radioForm = document.querySelector('#radio-form');
const partnerSignal = document.querySelector('#partner-signal');
const submitSignal = document.querySelector('#submit-signal');
const radioReconnect = document.querySelector('#radio-reconnect');

let state = null;
let archiveClue = null;
let archiveWrong = false;
let radioState = null;
let radioWrong = false;
let checked = false;
let online = false;
let busy = false;
let pendingRefresh = null;

openRoom.addEventListener('click', () => room.showModal());
document.querySelector('#close-engine').addEventListener('click', () => room.close());
document.querySelector('#return-island').addEventListener('click', () => room.close());
openArchive.addEventListener('click', () => {
  archive.showModal();
  refresh();
});
closeArchive.addEventListener('click', () => archive.close());
archiveReconnect.addEventListener('click', refresh);
archiveMarker.addEventListener('change', () => {
  archiveWrong = false;
  renderArchive();
});
openRadio.addEventListener('click', () => {
  radio.showModal();
  refresh();
});
document.querySelector('#close-radio').addEventListener('click', () => radio.close());
radioReconnect.addEventListener('click', refresh);

function writeText(element, text) {
  if (element.textContent !== text) element.textContent = text;
}

function render() {
  lobby.hidden = !checked || !!state;
  roomDetails.hidden = !state;
  enterRoom.disabled = busy;
  openRoom.disabled = !online || !state;
  openArchive.disabled = !online || !state;
  openRadio.disabled = !online || !state;
  if (state) {
    writeText(roomCode, state.code);
    writeText(roomPlayers, `${state.players.length} από 2 παίκτες · ${state.players.join(' · ')}`);
  }

  const steps = state?.steps ?? 0;
  const solved = state?.solved ?? false;
  controls.forEach(button => {
    button.disabled = busy || !online || !state || solved;
    button.classList.toggle('engaged', order.indexOf(button.dataset.action) < steps);
  });
  document.querySelectorAll('#signal-lights i').forEach((light, index) => light.classList.toggle('on', index < steps));
  document.querySelector('#signal-lights').setAttribute('aria-label', `${steps} από 3 μηχανισμούς ενεργοί`);
  writeText(document.querySelector('#engine-label'), solved ? 'Επανεκκίνηση ολοκληρώθηκε' : 'Ερεύνησε');
  document.querySelector('#power-finding').classList.toggle('empty', !solved);
  writeText(document.querySelector('#power-value'), solved ? `${state.power} V` : 'Δεν βρέθηκε ακόμη');
  writeText(document.querySelector('#power-display'), solved ? `${state.power} V` : '— V');
  const message = state?.message;
  const location = state?.location;
  writeText(document.querySelector('#progress'), `${Number(solved) + Number(!!message) + Number(!!location)} από 3 ευρήματα`);
  document.querySelector('#location-finding').classList.toggle('empty', !location);
  writeText(document.querySelector('#location-value'), location ?? 'Δεν βρέθηκε ακόμη');
  writeText(document.querySelector('#archive-label'), location ? 'Στίγμα επιβεβαιώθηκε' : 'Ερεύνησε');
  document.querySelector('#message-finding').classList.toggle('empty', !message);
  writeText(document.querySelector('#message-value'), message ?? 'Δεν βρέθηκε ακόμη');
  writeText(document.querySelector('#radio-label'), message ? 'Μήνυμα αποκωδικοποιήθηκε' : 'Ερεύνησε');
  renderRadio();
  renderArchive();
  const running = document.querySelector('#running-light');
  writeText(running, solved ? 'Σε λειτουργία' : 'Εκτός λειτουργίας');
  running.classList.toggle('on', solved);
  document.querySelector('#return-island').hidden = !solved;
}

function renderArchive() {
  const canSubmit = online && !busy && !!archiveClue && !state?.location;
  archiveMarker.disabled = !canSubmit;
  submitMarker.disabled = !canSubmit;
  archiveReconnect.hidden = online;
  for (const point of archiveMap.children) {
    point.classList.toggle('selected', point.textContent === archiveMarker.value);
  }
  writeText(document.querySelector('#archive-coordinates'), archiveClue?.coordinates ?? '—');
  writeText(document.querySelector('#archive-feedback'), !online ? 'Χάθηκε η σύνδεση με το φυλάκιο.'
    : state?.location ? `Το στίγμα ${state.location} καταγράφηκε στο κοινό σημειωματάριο.`
    : !archiveClue ? 'Φόρτωση του χάρτη…'
    : archiveWrong ? 'Αυτό το σημείο δεν ταιριάζει. Σύγκρινε ξανά τις συντεταγμένες.'
    : 'Τα σημεία περιλαμβάνουν δύο δοκιμαστικές θέσεις.');
}

function showArchiveMarkers(markers) {
  const points = Object.entries(markers).map(([marker, coordinates]) => {
    const [latitude, longitude] = coordinates.split(',').map(Number);
    return { marker, coordinates, latitude, longitude };
  });
  const north = Math.max(...points.map(point => point.latitude));
  const west = Math.min(...points.map(point => point.longitude));
  const latitudeSpan = north - Math.min(...points.map(point => point.latitude));
  const longitudeSpan = Math.max(...points.map(point => point.longitude)) - west;
  for (const { marker, coordinates, latitude, longitude } of points) {
    archiveMarker.add(new Option(`${marker} · ${coordinates}`, marker));
    const point = document.createElement('span');
    point.textContent = marker;
    point.style.top = `${20 + 60 * (north - latitude) / latitudeSpan}%`;
    point.style.left = `${14 + 72 * (longitude - west) / longitudeSpan}%`;
    archiveMap.append(point);
  }
  archiveMap.hidden = false;
}

function renderRadio() {
  const canConfirm = online && !busy && radioState?.ready && !radioState.confirmed;
  partnerSignal.disabled = !canConfirm;
  submitSignal.disabled = !canConfirm;
  radioReconnect.hidden = online;
  writeText(document.querySelector('#radio-signal'), radioState?.signal ?? '—');
  writeText(document.querySelector('#radio-feedback'), !online ? 'Χάθηκε η σύνδεση με τον ασύρματο.'
    : !radioState ? 'Σύνδεση με τον ασύρματο…'
    : radioState.message ? `Το μήνυμα αποκωδικοποιήθηκε. ${radioState.message}.`
    : !radioState.ready ? 'Περιμένεις τον δεύτερο παίκτη.'
    : radioState.confirmed ? 'Το σήμα επιβεβαιώθηκε. Περιμένεις τον συμπαίκτη σου.'
    : radioWrong ? 'Το σήμα δεν ταιριάζει. Έλεγξε τους χαρακτήρες με τον συμπαίκτη σου.'
    : radioState.partnerConfirmed ? 'Ο συμπαίκτης σου επιβεβαίωσε το σήμα. Είναι η σειρά σου.'
    : 'Καταχώρισε το σήμα του συμπαίκτη σου.');
}

function showConnection(connected, message) {
  online = connected;
  writeText(connection, message);
  connection.classList.toggle('error', !online);
  reconnect.hidden = online;
  roomReconnect.hidden = online;
}

function showMissingRoom() {
  const lostRoom = !!state;
  state = null;
  archiveClue = null;
  archiveWrong = false;
  archiveMarker.length = 1;
  archiveMap.replaceChildren();
  archiveMap.hidden = true;
  radioState = null;
  radioWrong = false;
  partnerSignal.value = '';
  checked = true;
  if (room.open) room.close();
  if (archive.open) archive.close();
  if (radio.open) radio.close();
  showConnection(true, lostRoom ? 'Η βάρδια έληξε. Δημιούργησε δωμάτιο ή μπες σε ένα υπάρχον.' : '');
  if (lostRoom) writeText(lobbyFeedback, 'Η προηγούμενη βάρδια έληξε.');
  render();
}

function showDisconnected() {
  showConnection(false, 'Χάθηκε η σύνδεση με τον φάρο. Δοκίμασε ξανά.');
  writeText(feedback, 'Χάθηκε η σύνδεση. Επανασύνδεσε τον φάρο πριν συνεχίσεις.');
  render();
}

function acceptState(next, action, stale = false) {
  const changed = !online || state?.revision !== next.revision;
  state = next;
  checked = true;
  showConnection(true, 'Η κοινή πρόοδος κρατιέται όσο ο φάρος είναι ανοιχτός.');
  if (changed || action) {
    writeText(feedback, state.solved ? `Η γεννήτρια ζωντάνεψε. Βρήκατε τη ρύθμιση των ${state.power} V.`
      : stale ? 'Η πρόοδος άλλαξε. Έλεγξε τους μηχανισμούς και δοκίμασε ξανά.'
      : action && state.steps === 0 ? 'Κάτι δεν πήγε σωστά. Ξεκίνα ξανά από τη βαλβίδα.'
      : state.steps > 0 ? 'Ο μηχανισμός αποκρίθηκε. Συνέχισε.'
      : 'Διάβασε το σημείωμα και δοκίμασε τους μηχανισμούς.');
  }
  render();
}

function postForm(path, form) {
  return fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(form),
    signal: AbortSignal.timeout(6000),
  });
}

async function loadState() {
  try {
    const response = await fetch(enginePath, { signal: AbortSignal.timeout(6000) });
    if (response.status === 401) {
      showMissingRoom();
      return;
    }
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    acceptState(await response.json());
    if (archive.open && !archiveClue) {
      const clue = await fetch(archivePath, { signal: AbortSignal.timeout(6000) });
      if (clue.status === 401) {
        showMissingRoom();
        return;
      }
      if (!clue.ok) throw new Error(`HTTP ${clue.status}`);
      archiveClue = await clue.json();
      showArchiveMarkers(archiveClue.markers);
      renderArchive();
    }
    if (radio.open) {
      await acceptRadio(await fetch(radioPath, { signal: AbortSignal.timeout(6000) }));
    }
  } catch {
    showDisconnected();
  }
}

document.querySelector('#archive-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (submitMarker.disabled) return;
  const marker = archiveMarker.value;
  busy = true;
  render();
  try {
    await pendingRefresh;
    if (!state || !online || state.location) return;
    const response = await postForm(archivePath, { marker });
    if (response.status === 401) {
      showMissingRoom();
      return;
    }
    if (!response.ok && response.status !== 422) throw new Error(`HTTP ${response.status}`);
    archiveWrong = response.status === 422;
    acceptState(await response.json());
  } catch {
    showDisconnected();
  } finally {
    busy = false;
    render();
    if (archive.open) {
      if (!online) archiveReconnect.focus();
      else if (state?.location) closeArchive.focus();
      else archiveMarker.focus();
    }
  }
});

async function acceptRadio(response) {
  if (response.status === 401) {
    showMissingRoom();
    return;
  }
  if (!response.ok && response.status !== 409 && response.status !== 422) {
    throw new Error(`HTTP ${response.status}`);
  }
  radioState = await response.json();
  if (radioState.message) state.message = radioState.message;
  render();
}

radioForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (submitSignal.disabled) return;
  const signal = partnerSignal.value;
  busy = true;
  render();
  try {
    await pendingRefresh;
    if (!state || !online) return;
    const response = await postForm(radioPath, { signal });
    radioWrong = response.status === 422;
    await acceptRadio(response);
    if (radioState?.confirmed) partnerSignal.value = '';
  } catch {
    showDisconnected();
  } finally {
    busy = false;
    render();
    if (radio.open) {
      if (!online) radioReconnect.focus();
      else if (!partnerSignal.disabled) partnerSignal.focus();
      else document.querySelector('#close-radio').focus();
    }
  }
});

function refresh() {
  if (!busy && !pendingRefresh) {
    pendingRefresh = loadState().finally(() => { pendingRefresh = null; });
  }
}

async function act(action, button) {
  if (busy || !online || !state) return;
  const { revision } = state;
  busy = true;
  render();
  try {
    await pendingRefresh;
    if (!state || !online) return;
    const response = await postForm(enginePath, { action, revision });
    if (response.status === 401) {
      showMissingRoom();
      return;
    }
    if (!response.ok && response.status !== 409) throw new Error(`HTTP ${response.status}`);
    acceptState(await response.json(), action, response.status === 409);
  } catch {
    // The POST may have reached the server. Refresh before allowing another action.
    showDisconnected();
  } finally {
    busy = false;
    render();
    if (room.open) {
      if (!online) roomReconnect.focus();
      else if (state.solved) document.querySelector('#return-island').focus();
      else button.focus();
    }
  }
}

guestForm.addEventListener('submit', async event => {
  event.preventDefault();
  if (busy) return;
  const form = new FormData(guestForm);
  busy = true;
  writeText(lobbyFeedback, '');
  render();
  try {
    await pendingRefresh;
    const response = await postForm('/api/room', form);
    if (response.ok) {
      acceptState(await response.json());
    } else if (response.status === 409 && (await response.text()) === 'Already in a room') {
      await loadState();
    } else {
      const messages = {
        400: 'Γράψε ένα όνομα και, για συμμετοχή, έναν έγκυρο κωδικό 8 χαρακτήρων.',
        404: 'Δεν βρέθηκε δωμάτιο με αυτόν τον κωδικό.',
        409: 'Το δωμάτιο έχει ήδη δύο παίκτες.',
        503: 'Ο φάρος δεν έχει χώρο για νέο δωμάτιο.',
      };
      writeText(lobbyFeedback, messages[response.status] ?? 'Δεν έγινε η σύνδεση. Δοκίμασε ξανά.');
    }
  } catch {
    // If the response was interrupted, check whether the guest cookie arrived.
    await loadState();
    if (!state) writeText(lobbyFeedback, 'Δεν έγινε η σύνδεση. Δοκίμασε ξανά.');
  } finally {
    busy = false;
    render();
    if (state) openRoom.focus();
  }
});

guestCode.addEventListener('input', () => {
  enterRoom.textContent = guestCode.value ? 'Μπες στο δωμάτιο' : 'Δημιούργησε δωμάτιο';
});
controls.forEach(button => button.addEventListener('click', () => act(button.dataset.action, button)));
reconnect.addEventListener('click', refresh);
roomReconnect.addEventListener('click', refresh);
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && state) refresh();
});
setInterval(() => {
  if (!document.hidden && state) refresh();
}, 1500);
refresh();

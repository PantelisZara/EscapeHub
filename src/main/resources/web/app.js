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

let state = null;
let checked = false;
let online = false;
let busy = false;
let pendingRefresh = null;

openRoom.addEventListener('click', () => room.showModal());
document.querySelector('#close-engine').addEventListener('click', () => room.close());
document.querySelector('#return-island').addEventListener('click', () => room.close());

function writeText(element, text) {
  if (element.textContent !== text) element.textContent = text;
}

function render() {
  lobby.hidden = !checked || !!state;
  roomDetails.hidden = !state;
  enterRoom.disabled = busy;
  openRoom.disabled = !online || !state;
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
  writeText(document.querySelector('#progress'), `${solved ? 1 : 0} από 3 ευρήματα`);
  const running = document.querySelector('#running-light');
  writeText(running, solved ? 'Σε λειτουργία' : 'Εκτός λειτουργίας');
  running.classList.toggle('on', solved);
  document.querySelector('#return-island').hidden = !solved;
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
  checked = true;
  if (room.open) room.close();
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
  } catch {
    showDisconnected();
  }
}

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

const room = document.querySelector('#engine-dialog');
const openRoom = document.querySelector('#open-engine');
const controls = [...document.querySelectorAll('[data-action]')];
const feedback = document.querySelector('#feedback');
const connection = document.querySelector('#connection');
const reconnect = document.querySelector('#reconnect');
const roomReconnect = document.querySelector('#room-reconnect');
let state = null;
let busy = false;

openRoom.addEventListener('click', () => room.showModal());
document.querySelector('#close-engine').addEventListener('click', () => room.close());
document.querySelector('#return-island').addEventListener('click', () => room.close());

function render() {
  openRoom.disabled = !state;
  controls.forEach(button => {
    button.disabled = busy || !state || state.solved;
    const order = ['open-valve', 'prime-pump', 'start-generator'];
    button.classList.toggle('engaged', !!state && order.indexOf(button.dataset.action) < state.steps);
  });
  if (!state) return;
  document.querySelectorAll('#signal-lights i').forEach((light, index) => light.classList.toggle('on', index < state.steps));
  document.querySelector('#signal-lights').setAttribute('aria-label', `${state.steps} από 3 μηχανισμούς ενεργοί`);
  document.querySelector('#engine-label').textContent = state.solved ? 'Επανεκκίνηση ολοκληρώθηκε' : 'Ερεύνησε';
  document.querySelector('#power-finding').classList.toggle('empty', !state.solved);
  document.querySelector('#power-value').textContent = state.solved ? `${state.power} V` : 'Δεν βρέθηκε ακόμη';
  document.querySelector('#power-display').textContent = state.solved ? `${state.power} V` : '— V';
  document.querySelector('#progress').textContent = `${state.solved ? 1 : 0} από 3 ευρήματα`;
  document.querySelector('#running-light').textContent = state.solved ? 'Σε λειτουργία' : 'Εκτός λειτουργίας';
  document.querySelector('#running-light').classList.toggle('on', state.solved);
  document.querySelector('#return-island').hidden = !state.solved;
}

async function request(action) {
  if (busy) return;
  busy = true;
  reconnect.hidden = true;
  roomReconnect.hidden = true;
  render();
  try {
    const response = await fetch('/api/engine-room', action ? {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ action }),
      signal: AbortSignal.timeout(6000),
    } : { signal: AbortSignal.timeout(6000) });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    state = await response.json();
    connection.textContent = 'Η πρόοδος κρατιέται όσο ο φάρος είναι ανοιχτός.';
    connection.classList.remove('error');
    feedback.textContent = state.solved ? 'Η γεννήτρια ζωντάνεψε. Κράτησες τη ρύθμιση: 240 V.'
      : action && state.steps === 0 ? 'Κάτι δεν πήγε σωστά. Ξεκίνα ξανά από τη βαλβίδα.'
      : state.steps > 0 ? 'Ο μηχανισμός αποκρίθηκε. Συνέχισε.'
      : 'Διάβασε το σημείωμα και δοκίμασε τους μηχανισμούς.';
  } catch {
    // A POST may have reached the server; read state before allowing another action.
    state = null;
    connection.textContent = 'Χάθηκε η σύνδεση. Δοκίμασε ξανά για να συνεχίσεις.';
    connection.classList.add('error');
    feedback.textContent = 'Χάθηκε η σύνδεση. Επανασύνδεσε τον φάρο πριν συνεχίσεις.';
    reconnect.hidden = false;
    roomReconnect.hidden = false;
  } finally {
    busy = false;
    render();
    if (room.open && action) {
      if (!state) roomReconnect.focus();
      else if (state.solved) document.querySelector('#return-island').focus();
      else controls.find(button => button.dataset.action === action).focus();
    }
  }
}

controls.forEach(button => button.addEventListener('click', () => request(button.dataset.action)));
reconnect.addEventListener('click', () => request());
roomReconnect.addEventListener('click', () => request());
request();

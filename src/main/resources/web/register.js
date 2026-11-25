const registrationForm = document.querySelector('#registration-form');
const registrationPassword = document.querySelector('#registration-password');
const registrationButton = registrationForm.querySelector('button[type="submit"]');
const registrationFeedback = document.querySelector('#registration-feedback');

registrationForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const passwordLength = Array.from(registrationPassword.value).length;
  if (passwordLength < 8 || passwordLength > 128) {
    registrationFeedback.textContent = 'Ο κωδικός πρόσβασης πρέπει να έχει 8–128 χαρακτήρες.';
    registrationPassword.focus();
    return;
  }

  const body = new URLSearchParams(new FormData(registrationForm));
  registrationButton.disabled = true;
  registrationFeedback.textContent = 'Δημιουργία λογαριασμού…';
  try {
    const response = await fetch(registrationForm.action, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body,
    });
    if (response.status === 201) {
      registrationForm.reset();
      registrationFeedback.textContent = 'Ο λογαριασμός δημιουργήθηκε.';
    } else if (response.status === 409) {
      registrationFeedback.textContent = 'Αυτό το όνομα χρήστη χρησιμοποιείται ήδη.';
      registrationForm.elements.username.focus();
    } else if (response.status === 400 || response.status === 413) {
      registrationFeedback.textContent = 'Έλεγξε το όνομα χρήστη και τον κωδικό πρόσβασης.';
    } else {
      registrationFeedback.textContent = 'Η εγγραφή δεν είναι διαθέσιμη αυτή τη στιγμή. Δοκίμασε ξανά.';
    }
  } catch {
    registrationFeedback.textContent = 'Η σύνδεση διακόπηκε. Δοκίμασε ξανά.';
  } finally {
    registrationButton.disabled = false;
  }
});

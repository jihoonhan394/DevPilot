// Keeps the browser's install prompt for the app (docs/02 §8.2). Loaded before the app, so an
// early beforeinstallprompt event is not lost. A separate file because the CSP allows no inline
// script.
window.addEventListener('beforeinstallprompt', function (event) {
  event.preventDefault();
  window.devpilotInstallEvent = event;
  window.dispatchEvent(new Event('devpilot-install-change'));
});

window.addEventListener('appinstalled', function () {
  window.devpilotInstallEvent = null;
  window.dispatchEvent(new Event('devpilot-install-change'));
});

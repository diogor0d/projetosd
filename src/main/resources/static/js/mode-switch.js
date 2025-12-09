// Compute and set CSS variables --pill-left and --pill-width on each mode switch
(function () {
  function updateSwitchPill(switchEl) {
    if (!switchEl) return;
    const checkedInput = switchEl.querySelector('input[type="radio"]:checked');
    // if none checked, try first option
    const input = checkedInput || switchEl.querySelector('input[type="radio"]');
    if (!input) return;
    const label = input.nextElementSibling; // the .mode-label span
    if (!label) return;

    const containerRect = switchEl.getBoundingClientRect();
    const labelRect = label.getBoundingClientRect();

    // compute left relative to container
    const left = Math.round(labelRect.left - containerRect.left);
    const width = Math.round(labelRect.width);

    // set CSS variables on the container
    switchEl.style.setProperty('--pill-left', left + 'px');
    switchEl.style.setProperty('--pill-width', width + 'px');
  }

  function updateAll() {
    document.querySelectorAll('.page-mode-switch, .search-mode-switch').forEach(updateSwitchPill);
  }

  function onRadioChange(e) {
    const switchEl = e.target.closest('.page-mode-switch, .search-mode-switch');
    if (switchEl) updateSwitchPill(switchEl);
  }

  document.addEventListener('DOMContentLoaded', function () {
    // initial update
    updateAll();

    // attach listeners to radios
    document.querySelectorAll('.page-mode-switch, .search-mode-switch').forEach(function (switchEl) {
      switchEl.addEventListener('change', onRadioChange);
    });

    // update on window resize (debounced)
    let resizeTimer = null;
    window.addEventListener('resize', function () {
      clearTimeout(resizeTimer);
      resizeTimer = setTimeout(updateAll, 120);
    });

    // also observe DOM mutations in case fonts load or layout shifts
    const obs = new MutationObserver(function () {
      updateAll();
    });
    obs.observe(document.body, { childList: true, subtree: true, attributes: true });
  });
})();

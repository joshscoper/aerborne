// Admin helpers: slug suggestions, image previews and confirm-before-delete.
(function () {
  "use strict";

  function slugify(text) {
    return text.toLowerCase().normalize("NFKD").replace(/[̀-ͯ]/g, "")
      .replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 80);
  }

  document.addEventListener("DOMContentLoaded", function () {
    // Fill the slug from the title until someone edits the slug by hand.
    Array.prototype.forEach.call(document.querySelectorAll("[data-slug-for]"), function (slug) {
      var source = document.querySelector('[name="' + slug.getAttribute("data-slug-for") + '"]');
      if (!source) return;
      var touched = slug.value !== "";
      slug.addEventListener("input", function () { touched = true; });
      source.addEventListener("input", function () {
        if (!touched) slug.value = slugify(source.value);
      });
    });

    Array.prototype.forEach.call(document.querySelectorAll("[data-image-select]"), function (select) {
      var preview = select.parentNode.querySelector("[data-image-preview]");
      if (!preview) return;
      select.addEventListener("change", function () {
        var v = select.value;
        if (!v) { preview.hidden = true; return; }
        if (v.indexOf("static:") === 0) preview.src = preview.getAttribute("data-static-base") + v.slice(7);
        else if (v.indexOf("media:") === 0) preview.src = preview.getAttribute("data-media-base") + v.slice(6);
        preview.hidden = false;
      });
    });

    Array.prototype.forEach.call(document.querySelectorAll("form[data-confirm]"), function (form) {
      form.addEventListener("submit", function (event) {
        if (!window.confirm(form.getAttribute("data-confirm"))) event.preventDefault();
      });
    });
  });
})();

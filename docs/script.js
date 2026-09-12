(() => {
  "use strict";

  const root = document.documentElement;
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  const finePointer = window.matchMedia("(hover: hover) and (pointer: fine)");
  const progressFill = document.querySelector(".signal-progress__fill");
  const progressHead = document.querySelector(".signal-progress__head");
  const navLinks = [...document.querySelectorAll("[data-nav]")];
  const scenes = [...document.querySelectorAll(".scene[id]")];

  const clamp = (value, min = 0, max = 1) => Math.min(max, Math.max(min, value));

  function updateDocumentProgress() {
    const max = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
    const progress = clamp(window.scrollY / max);
    if (progressFill) progressFill.style.transform = `scaleX(${progress})`;
    if (progressHead) progressHead.style.transform = `translateX(calc(${progress * 100}vw - 50%))`;
  }

  let progressFrame = 0;
  function requestProgressUpdate() {
    if (progressFrame) return;
    progressFrame = requestAnimationFrame(() => {
      progressFrame = 0;
      updateDocumentProgress();
    });
  }

  updateDocumentProgress();
  window.addEventListener("scroll", requestProgressUpdate, { passive: true });
  window.addEventListener("resize", requestProgressUpdate, { passive: true });

  if ("IntersectionObserver" in window) {
    const sceneObserver = new IntersectionObserver((entries) => {
      const visible = entries
        .filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
      if (!visible) return;
      const activeId = visible.target.id;
      navLinks.forEach((link) => link.classList.toggle("is-active", link.dataset.nav === activeId));
    }, { rootMargin: "-25% 0px -55% 0px", threshold: [0, .1, .25, .5] });
    scenes.forEach((scene) => sceneObserver.observe(scene));
  }

  document.querySelectorAll(".pointer-field").forEach((field) => {
    const setPointer = (event) => {
      const rect = field.getBoundingClientRect();
      const x = clamp((event.clientX - rect.left) / rect.width);
      const y = clamp((event.clientY - rect.top) / rect.height);
      field.style.setProperty("--pointer-x", `${x * 100}%`);
      field.style.setProperty("--pointer-y", `${y * 100}%`);
      field.style.setProperty("--gx", `${x * 100}%`);
      field.style.setProperty("--gy", `${y * 100}%`);

      if (field.classList.contains("gesture-stage")) {
        const dx = x - .5;
        const dy = y - .5;
        const zone = Math.abs(dx) > Math.abs(dy)
          ? (dx < 0 ? "left" : "right")
          : (dy < 0 ? "top" : "bottom");
        field.dataset.zone = zone;
      }
    };
    field.addEventListener("pointermove", setPointer, { passive: true });
    field.addEventListener("pointerleave", () => {
      field.style.setProperty("--pointer-x", "50%");
      field.style.setProperty("--pointer-y", "50%");
      field.style.setProperty("--gx", "50%");
      field.style.setProperty("--gy", "50%");
      if (field.classList.contains("gesture-stage")) delete field.dataset.zone;
    });
  });

  function initMagnetic() {
    if (!finePointer.matches || reducedMotion.matches) return;
    document.querySelectorAll(".magnetic").forEach((button) => {
      const move = (event) => {
        const rect = button.getBoundingClientRect();
        const x = (event.clientX - (rect.left + rect.width / 2)) * .12;
        const y = (event.clientY - (rect.top + rect.height / 2)) * .18;
        button.style.transform = `translate3d(${x}px, ${y}px, 0)`;
      };
      button.addEventListener("pointermove", move, { passive: true });
      button.addEventListener("pointerleave", () => { button.style.transform = ""; });
    });
  }

  function initGsap() {
    const gsap = window.gsap;
    const ScrollTrigger = window.ScrollTrigger;
    if (!gsap || !ScrollTrigger || reducedMotion.matches) return;

    gsap.registerPlugin(ScrollTrigger);

    const intro = gsap.timeline({ defaults: { ease: "power4.out" } });
    intro
      .from(".hero-word--thyst", { xPercent: -22, duration: .9 }, 0)
      .from(".hero-word--tv", { xPercent: 38, duration: .9 }, 0)
      .from(".hero-stage", { xPercent: 15, scale: .94, duration: 1.05 }, .08)
      .from(".hero-copy .eyebrow, .hero-deck, .hero-actions", { x: -22, autoAlpha: 0, duration: .62, stagger: .07 }, .18)
      .from(".hero-ledger", { scaleX: .6, transformOrigin: "left", autoAlpha: 0, duration: .55 }, .38);

    gsap.to(".hero-word--thyst", {
      xPercent: 12,
      ease: "none",
      scrollTrigger: { trigger: ".hero", start: "top top", end: "bottom top", scrub: .7 }
    });
    gsap.to(".hero-word--tv", {
      xPercent: -18,
      ease: "none",
      scrollTrigger: { trigger: ".hero", start: "top top", end: "bottom top", scrub: .7 }
    });
    gsap.to(".broadcast-window", {
      rotateY: 2,
      xPercent: 4,
      ease: "none",
      scrollTrigger: { trigger: ".hero", start: "top top", end: "bottom top", scrub: .8 }
    });

    const gestureScreen = document.querySelector(".gesture-screen");
    if (gestureScreen) {
      const xTo = gsap.quickTo(gestureScreen, "x", { duration: .35, ease: "power3.out" });
      const yTo = gsap.quickTo(gestureScreen, "y", { duration: .35, ease: "power3.out" });
      const gestureStage = document.querySelector(".gesture-stage");
      gestureStage?.addEventListener("pointermove", (event) => {
        if (!finePointer.matches) return;
        const rect = gestureStage.getBoundingClientRect();
        xTo(((event.clientX - rect.left) / rect.width - .5) * 18);
        yTo(((event.clientY - rect.top) / rect.height - .5) * 12);
      }, { passive: true });
      gestureStage?.addEventListener("pointerleave", () => { xTo(0); yTo(0); });
    }

    gsap.from(".quality-slice", {
      xPercent: 22,
      rotate: 2,
      scrollTrigger: { trigger: ".player", start: "35% 75%", end: "75% 45%", scrub: .8 }
    });

    initEscapeSequence(gsap, ScrollTrigger);
    initMorphSequence(gsap, ScrollTrigger);

    gsap.from(".stats-shot", {
      xPercent: -14,
      rotate: -2.5,
      scale: .94,
      scrollTrigger: { trigger: ".local", start: "top 70%", end: "45% 45%", scrub: .8 }
    });
    gsap.from(".local-statement", {
      xPercent: 18,
      scrollTrigger: { trigger: ".local", start: "20% 75%", end: "65% 55%", scrub: .7 }
    });
    gsap.to(".local-type span", {
      xPercent: (index) => [-8, 12, -14, 6][index] || 0,
      stagger: .04,
      scrollTrigger: { trigger: ".local", start: "top bottom", end: "bottom top", scrub: 1 }
    });

    gsap.from(".source-grid > *", {
      x: (index) => index % 2 ? 26 : -26,
      autoAlpha: 0,
      stagger: .08,
      duration: .7,
      ease: "power3.out",
      scrollTrigger: { trigger: ".source", start: "top 62%", toggleActions: "play none none reverse" }
    });

    gsap.to(".final-signal span", {
      rotation: "+=12",
      scale: (index) => 1 + index * .025,
      stagger: .02,
      scrollTrigger: { trigger: ".final", start: "top bottom", end: "bottom top", scrub: 1.2 }
    });

    ScrollTrigger.refresh();
  }

  function initEscapeSequence(gsap, ScrollTrigger) {
    const section = document.querySelector(".escape");
    const video = document.querySelector("#floating-chat-video");
    const shell = document.querySelector(".video-shell");
    const meter = document.querySelector(".scrub-meter i");
    if (!section || !video || !shell) return;

    const fragments = [...document.querySelectorAll(".escape-fragment")];
    const escapeMark = document.querySelector(".escape-mark");
    const escapeTl = gsap.timeline({ paused: true, defaults: { ease: "none" } });
    escapeTl
      .to(shell, { scale: 1.08, clipPath: "polygon(0 0,100% 0,100% 100%,0 100%)", duration: .25 }, 0)
      .to(fragments[0], { x: "-22vw", y: "-13vh", rotation: -5, autoAlpha: 1, duration: .45 }, .08)
      .to(fragments[1], { x: "24vw", y: "-16vh", rotation: 4, autoAlpha: 1, duration: .45 }, .12)
      .to(fragments[2], { x: "26vw", y: "2vh", rotation: -2, autoAlpha: 1, duration: .45 }, .18)
      .to(fragments[3], { x: "-24vw", y: "13vh", rotation: 3, autoAlpha: 1, duration: .45 }, .22)
      .to(fragments[4], { x: "18vw", y: "17vh", rotation: -4, autoAlpha: 1, duration: .45 }, .27)
      .to(shell, { scale: .82, rotation: -.8, duration: .35 }, .62)
      .to(fragments, { x: 0, y: 0, rotation: 0, scale: .82, autoAlpha: .14, stagger: .012, duration: .25 }, .74)
      .to(escapeMark, { rotation: 82, scale: 1.2, autoAlpha: .32, duration: .8 }, 0);

    let duration = 0;
    let desiredTime = 0;
    let seekFrame = 0;

    const syncVideo = (progress) => {
      if (!duration || !Number.isFinite(duration)) return;
      desiredTime = clamp(progress) * Math.max(0, duration - .04);
      if (seekFrame) return;
      seekFrame = requestAnimationFrame(() => {
        seekFrame = 0;
        if (Math.abs(video.currentTime - desiredTime) < .025) return;
        try { video.currentTime = desiredTime; } catch (_) { /* seeking can fail before metadata settles */ }
      });
    };

    const readDuration = () => {
      if (Number.isFinite(video.duration) && video.duration > 0) duration = video.duration;
    };
    video.addEventListener("loadedmetadata", readDuration, { once: true });
    if (video.readyState >= 1) readDuration();

    ScrollTrigger.create({
      trigger: section,
      start: "top top",
      end: "bottom bottom",
      scrub: .5,
      onUpdate: (self) => {
        escapeTl.progress(self.progress);
        syncVideo(self.progress);
        if (meter) meter.style.transform = `scaleX(${self.progress})`;
      },
      onLeave: () => { video.pause(); },
      onLeaveBack: () => { video.pause(); }
    });
  }

  function initMorphSequence(gsap, ScrollTrigger) {
    const section = document.querySelector(".recompose");
    const frame = document.querySelector(".morph-frame");
    const labels = [...document.querySelectorAll(".morph-scale span")];
    if (!section || !frame) return;

    const tl = gsap.timeline({ paused: true, defaults: { ease: "power2.inOut" } });
    tl
      .fromTo(frame,
        { clipPath: "inset(0 35% 0 35%)", scale: .86, rotation: -1.2 },
        { clipPath: "inset(16% 4% 16% 4%)", scale: .95, rotation: 0, duration: 1 }, 0)
      .to(frame, { clipPath: "inset(2% 14% 2% 14%)", scale: 1, rotation: .35, duration: 1 }, 1)
      .to(frame, { clipPath: "inset(18% 0 18% 0)", scale: 1.08, rotation: 0, duration: 1 }, 2);

    ScrollTrigger.create({
      trigger: section,
      start: "top top",
      end: "bottom bottom",
      scrub: .7,
      onUpdate: (self) => {
        tl.progress(self.progress);
        const step = Math.min(3, Math.floor(self.progress * 4));
        labels.forEach((label, index) => label.classList.toggle("is-active", index === step));
      }
    });
  }

  function start() {
    initMagnetic();
    initGsap();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start, { once: true });
  } else {
    start();
  }
})();

(() => {
    const data = window.PORTFOLIO_DATA;

    // DOM
    const dom = {
        heroKicker: document.getElementById("hero-kicker"),
        heroTitle: document.getElementById("hero-title"),
        heroSummary: document.getElementById("hero-summary"),
        heroNote: document.getElementById("hero-note"),
        focusTitle: document.getElementById("focus-title"),
        focusBody: document.getElementById("focus-body"),
        focusFacts: document.getElementById("focus-facts"),
        heroHighlights: document.getElementById("hero-highlights"),
        metricGrid: document.getElementById("metric-grid"),
        aboutTitle: document.getElementById("about-title"),
        aboutCopy: document.getElementById("about-copy"),
        experienceList: document.getElementById("experience-list"),
        projectList: document.getElementById("project-list"),
        skillsGrid: document.getElementById("skills-grid"),
        awardList: document.getElementById("award-list"),
        heroActions: document.getElementById("hero-actions"),
        contactActions: document.getElementById("contact-actions"),
        contactTitle: document.getElementById("contact-title"),
        contactCopy: document.getElementById("contact-copy"),
        contactLocation: document.getElementById("contact-location"),
        contactNote: document.getElementById("contact-note"),
        footerName: document.getElementById("footer-name"),
        currentYear: document.getElementById("current-year"),
        clock: document.getElementById("ist-time")
    };

    // Helpers
    function createNode(tagName, className) {
        const element = document.createElement(tagName);

        if (className) {
            element.className = className;
        }

        return element;
    }

    function createLink(link, className = "button button--secondary") {
        const anchor = createNode("a", className);
        anchor.textContent = link.label;
        anchor.href = link.url;
        anchor.target = link.url.startsWith("mailto:") ? "_self" : "_blank";
        anchor.rel = link.url.startsWith("mailto:") ? "" : "noreferrer";
        return anchor;
    }

    function renderActions(target, links) {
        const configuredLinks = links.filter((link) => link.url);

        configuredLinks.forEach((link) => {
            const variant =
                link.style === "primary"
                    ? "button button--primary"
                    : link.style === "ghost"
                        ? "button button--ghost"
                        : "button button--secondary";

            target.appendChild(createLink(link, variant));
        });
    }

    // Content
    function populateStaticCopy() {
        dom.heroKicker.textContent = data.profile.heroKicker;
        dom.heroTitle.textContent = data.profile.heroTitle;
        dom.heroSummary.textContent = data.profile.heroSummary;
        dom.heroNote.textContent = data.profile.heroNote;
        dom.focusTitle.textContent = data.profile.focusTitle;
        dom.focusBody.textContent = data.profile.focusBody;
        dom.aboutTitle.textContent = data.profile.aboutTitle;
        dom.aboutCopy.textContent = data.profile.aboutCopy;
        dom.contactTitle.textContent = data.contact.title;
        dom.contactCopy.textContent = data.contact.copy;
        dom.contactLocation.textContent = `${data.profile.location} - ${data.profile.email}`;
        dom.contactNote.textContent = data.contact.note;
        dom.footerName.textContent = data.profile.name;
        dom.currentYear.textContent = new Date().getFullYear();
    }

    function renderFacts() {
        data.profile.focusFacts.forEach((fact) => {
            const wrapper = createNode("div", "fact-list__item");
            const label = createNode("dt");
            const value = createNode("dd");

            label.textContent = fact.label;
            value.textContent = fact.value;

            wrapper.append(label, value);
            dom.focusFacts.appendChild(wrapper);
        });
    }

    function renderHeroHighlights() {
        data.heroHighlights.forEach((item) => {
            const badge = createNode("div", "hero-highlight");
            badge.textContent = item;
            dom.heroHighlights.appendChild(badge);
        });
    }

    function renderMetrics() {
        data.metrics.forEach((metric) => {
            const card = createNode("article", "metric-card panel");
            card.setAttribute("data-reveal", "");

            card.innerHTML = `
                <p class="metric-card__value">${metric.value}</p>
                <h3 class="metric-card__label">${metric.label}</h3>
                <p class="metric-card__detail">${metric.detail}</p>
            `;

            dom.metricGrid.appendChild(card);
        });
    }

    function renderExperience() {
        data.experience.forEach((item) => {
            const article = createNode("article", "timeline-card panel");
            const tags = item.tags
                .map((tag) => `<span class="chip">${tag}</span>`)
                .join("");

            article.setAttribute("data-reveal", "");
            article.innerHTML = `
                <div class="timeline-card__header">
                    <div>
                        <p class="timeline-card__period">${item.period}</p>
                        <h3>${item.role}</h3>
                        <p class="timeline-card__company">${item.company}</p>
                    </div>
                </div>
                <p class="timeline-card__summary">${item.summary}</p>
                <ul class="timeline-card__list">${item.bullets.map((bullet) => `<li>${bullet}</li>`).join("")}</ul>
                <div class="chip-row">${tags}</div>
            `;

            dom.experienceList.appendChild(article);
        });
    }

    function renderProjects() {
        data.projects.forEach((project) => {
            const card = createNode("article", "project-card panel");
            const stack = project.stack
                .map((item) => `<span class="chip">${item}</span>`)
                .join("");
            const metrics = project.metrics
                .map(
                    (metric) => `
                        <div class="project-stat">
                            <span class="project-stat__value">${metric.value}</span>
                            <span class="project-stat__label">${metric.label}</span>
                        </div>
                    `
                )
                .join("");
            const links = project.links.filter((link) => link.url);
            const actions = links.length
                ? `<div class="project-card__actions">${links
                    .map(
                        (link) => `
                            <a class="button button--ghost" href="${link.url}" target="_blank" rel="noreferrer">${link.label}</a>
                        `
                    )
                    .join("")}</div>`
                : "";

            card.setAttribute("data-reveal", "");
            card.innerHTML = `
                <div class="project-card__intro">
                    <p class="eyebrow">${project.category}</p>
                    <h3>${project.title}</h3>
                    <p class="project-card__description">${project.description}</p>
                    <p class="project-card__outcome">${project.outcome}</p>
                </div>
                <div class="project-card__metrics">${metrics}</div>
                <div class="project-card__layout">
                    <ul class="project-card__list">${project.bullets.map((bullet) => `<li>${bullet}</li>`).join("")}</ul>
                    <div>
                        <div class="chip-row">${stack}</div>
                        ${actions}
                    </div>
                </div>
            `;

            dom.projectList.appendChild(card);
        });
    }

    function renderSkills() {
        data.skills.forEach((group) => {
            const card = createNode("article", "skill-card panel");
            const items = group.items
                .map((item) => `<span class="skill-pill">${item}</span>`)
                .join("");

            card.setAttribute("data-reveal", "");
            card.innerHTML = `
                <p class="panel__eyebrow">${group.title}</p>
                <div class="skill-pill-wrap">${items}</div>
            `;

            dom.skillsGrid.appendChild(card);
        });
    }

    function renderAwards() {
        data.awards.forEach((award) => {
            const item = createNode("article", "award-item");

            item.innerHTML = `
                <h3>${award.title}</h3>
                <p class="award-item__org">${award.org}</p>
                <p>${award.detail}</p>
            `;

            dom.awardList.appendChild(item);
        });
    }

    // Motion
    function updateClock() {
        const formatter = new Intl.DateTimeFormat("en-IN", {
            hour: "2-digit",
            minute: "2-digit",
            timeZone: "Asia/Kolkata"
        });

        dom.clock.textContent = `Bengaluru time - ${formatter.format(new Date())}`;
    }

    function parseCountValue(text) {
        const match = text.trim().match(/(\d+(?:\.\d+)?)/);

        if (!match) {
            return null;
        }

        const number = match[1];

        return {
            value: parseFloat(number),
            decimals: number.includes(".") ? number.split(".")[1].length : 0,
            prefix: text.slice(0, match.index),
            suffix: text.slice(match.index + number.length)
        };
    }

    function setupCountUp() {
        const observer = new IntersectionObserver(
            (entries) => {
                entries.forEach((entry) => {
                    if (!entry.isIntersecting || entry.target.dataset.counted === "true") {
                        return;
                    }

                    const target = entry.target;
                    const parsed =
                        target.dataset.countup
                            ? {
                                value: parseFloat(target.dataset.countup),
                                decimals: (target.dataset.countup.split(".")[1] || "").length,
                                prefix: target.dataset.prefix || "",
                                suffix: target.dataset.suffix || ""
                            }
                            : parseCountValue(target.textContent);

                    if (!parsed) {
                        observer.unobserve(target);
                        return;
                    }

                    const duration = 1200;
                    const start = performance.now();

                    function tick(now) {
                        const progress = Math.min((now - start) / duration, 1);
                        const eased = 1 - Math.pow(1 - progress, 3);
                        const current = parsed.value * eased;

                        target.textContent = `${parsed.prefix}${current.toFixed(parsed.decimals)}${parsed.suffix}`;

                        if (progress < 1) {
                            requestAnimationFrame(tick);
                        } else {
                            target.dataset.counted = "true";
                        }
                    }

                    requestAnimationFrame(tick);
                    observer.unobserve(target);
                });
            },
            { threshold: 0.45 }
        );

        document
            .querySelectorAll(".metric-card__value, .project-stat__value, [data-countup]")
            .forEach((element) => observer.observe(element));
    }

    function setupReveal() {
        const observer = new IntersectionObserver(
            (entries) => {
                entries.forEach((entry) => {
                    if (entry.isIntersecting) {
                        entry.target.classList.add("is-visible");
                        observer.unobserve(entry.target);
                    }
                });
            },
            { threshold: 0.14 }
        );

        document.querySelectorAll("[data-reveal]").forEach((element, index) => {
            element.style.setProperty("--reveal-delay", `${Math.min(index * 40, 180)}ms`);
            observer.observe(element);
        });
    }

    function setupPointerMotion() {
        const root = document.documentElement;

        window.addEventListener("pointermove", (event) => {
            root.style.setProperty("--pointer-x", (event.clientX / window.innerWidth).toFixed(3));
            root.style.setProperty("--pointer-y", (event.clientY / window.innerHeight).toFixed(3));
        });
    }

    function setupSurfaceLighting() {
        document.querySelectorAll(".panel, .button, .hero-highlight").forEach((element) => {
            element.addEventListener("pointermove", (event) => {
                const rect = element.getBoundingClientRect();
                const x = ((event.clientX - rect.left) / rect.width) * 100;
                const y = ((event.clientY - rect.top) / rect.height) * 100;

                element.style.setProperty("--spotlight-x", `${x}%`);
                element.style.setProperty("--spotlight-y", `${y}%`);
            });

            element.addEventListener("pointerleave", () => {
                element.style.removeProperty("--spotlight-x");
                element.style.removeProperty("--spotlight-y");
            });
        });
    }

    // Init
    function init() {
        populateStaticCopy();
        renderActions(dom.heroActions, data.socials);
        renderActions(dom.contactActions, data.socials);
        renderHeroHighlights();
        renderFacts();
        renderMetrics();
        renderExperience();
        renderProjects();
        renderSkills();
        renderAwards();
        updateClock();
        setupReveal();
        setupPointerMotion();
        setupSurfaceLighting();
        setupCountUp();
        setInterval(updateClock, 30000);
    }

    init();
})();

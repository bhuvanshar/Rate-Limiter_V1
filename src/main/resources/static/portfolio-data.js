// Edit this file to update personal links, project links, copy, and cards.
window.PORTFOLIO_DATA = {
    profile: {
        name: "Bhuvan Sharma",
        role: "Software Development Engineer",
        location: "Bengaluru, India",
        email: "bhuvan.sharma.bits24@gmail.com",
        phone: "+91 9001807536",
        heroKicker: "Portfolio 2026 - Bengaluru - Backend Systems",
        heroTitle: "Backend engineer for product teams that need speed, stability, and trust.",
        heroSummary:
            "Software Development Engineer with 3 years of experience building scalable Java and Spring Boot systems, improving production performance, and shipping reliable workflows for enterprise banking teams.",
        heroNote:
            "I build APIs, automation, and backend workflows that reduce operational friction, improve performance, and hold up when real production pressure hits.",
        focusTitle: "Shipping enterprise-grade financial workflow systems at MBB Labs for Maybank.",
        focusBody:
            "My recent work spans enterprise loan processing, service integrations, production issue resolution, automation, and backend improvements that make internal platforms faster, safer, and more dependable for the teams using them every day.",
        focusFacts: [
            { label: "Role", value: "Software Development Engineer" },
            { label: "Current team", value: "MBB Labs Pvt. Ltd (Maybank)" },
            { label: "Education", value: "BITS Pilani - B.E. - CGPA 7.8" }
        ],
        aboutTitle: "I build software that earns confidence by being fast, stable, and easy to rely on.",
        aboutCopy:
            "That usually means APIs that stay predictable, workflows that remove manual effort, and internal platforms that support real teams without adding operational noise. I'm strongest on the backend, but I also care about frontend clarity because great software should read cleanly at every layer."
    },

    heroHighlights: [
        "500+ internal banking users supported daily",
        "250 workflow artifacts automated every day",
        "99.8% throttling accuracy on rate-limiting workloads"
    ],

    socials: [
        {
            label: "LinkedIn",
            url: "https://www.linkedin.com/in/bhuvan-sharma-59a5b9157/",
            style: "primary"
        },
        {
            label: "Email",
            url: "mailto:bhuvan.sharma.bits24@gmail.com",
            style: "secondary"
        },
        {
            label: "GitHub",
            url: "",
            style: "ghost"
        }
    ],

    metrics: [
        {
            value: "30+",
            label: "Spring Boot APIs delivered",
            detail: "Supporting workflow orchestration, async processing, and enterprise integrations."
        },
        {
            value: "60%",
            label: "Critical flow performance gain",
            detail: "Reduced key page response time from roughly 5 seconds to 2 seconds."
        },
        {
            value: "5K+",
            label: "Concurrent users supported",
            detail: "Handled by the API rate limiter project while keeping memory usage lean."
        }
    ],

    experience: [
        {
            role: "Software Development Engineer",
            company: "MBB Labs Pvt. Ltd (Maybank)",
            period: "July 2024 - Present",
            summary:
                "Working on enterprise financial workflow systems used by internal banking teams to process business loans and related operations.",
            bullets: [
                "Built and maintained backend services for the Business Enablers & Solution Technology platform used daily by roughly 500 banking employees.",
                "Automated cross-workflow document synchronization, removing manual intervention for around 250 workflow artifacts every day.",
                "Created Spring Batch automation for pre and post installation validation, improving deployment readiness and reducing manual verification effort.",
                "Optimized database queries and backend logic to improve a critical user flow from around 5 seconds to 2 seconds.",
                "Resolved a production-critical transaction issue tied to high-volume upload failures, unblocking releases and stabilizing the flow."
            ],
            tags: ["Java", "Spring Boot", "Microservices", "Oracle SQL", "Production Support"]
        },
        {
            role: "Software Development Engineer Intern",
            company: "MBB Labs Pvt. Ltd (Maybank)",
            period: "July 2023 - June 2024",
            summary:
                "Contributed across backend API development and frontend performance improvements while working closely with product and engineering teams.",
            bullets: [
                "Developed and optimized REST APIs for the TMNS team using Java, Spring Boot, and JPA, cutting server response times by 30%.",
                "Designed ReactJS components and managed state with Redux, reducing page load time by 20% and improving usability."
            ],
            tags: ["REST APIs", "ReactJS", "Redux", "JPA", "Performance"]
        }
    ],

    projects: [
        {
            title: "API Rate Limiter",
            category: "Spring Boot - MySQL - Concurrency Design",
            description:
                "A production-style throttling platform built to protect APIs from abuse while giving operators granular control over limits by user, IP, and endpoint.",
            outcome:
                "Achieved 99.8% request throttling accuracy with less than 5ms overhead per request while efficiently supporting 5,000+ concurrent users on 2GB of heap memory.",
            bullets: [
                "Implemented token bucket and sliding window strategies behind a clean strategy pattern for extensibility.",
                "Kept the request hot path in memory while moving rule management and audit logging into a hybrid persistence model.",
                "Designed composite key resolution for user, IP, API, and combined dimensions to support fine-grained throttling rules."
            ],
            stack: ["Java", "Spring Boot", "MySQL", "ConcurrentHashMap", "Caffeine Cache", "Micrometer"],
            metrics: [
                { label: "Accuracy", value: "99.8%" },
                { label: "Overhead", value: "<5ms" },
                { label: "Users", value: "5,000+" }
            ],
            links: [
                {
                    label: "GitHub Repo",
                    url: ""
                },
                {
                    label: "Live Demo",
                    url: ""
                }
            ]
        }
    ],

    skills: [
        {
            title: "Backend",
            items: ["Java", "Spring Boot", "Spring Security", "Spring Batch", "Microservices", "REST APIs"]
        },
        {
            title: "Frontend",
            items: ["HTML5", "CSS", "JavaScript", "ReactJS", "React Native"]
        },
        {
            title: "Databases",
            items: ["Oracle SQL", "MySQL", "MongoDB"]
        },
        {
            title: "Core Strengths",
            items: ["System Design", "Data Structures", "OOP", "Performance Tuning", "Production Debugging"]
        }
    ],

    awards: [
        {
            title: "Group Technology Plaque of Achievement",
            org: "Maybank",
            detail: "Recognized for contributions to the Business Enablers & Solution Technology team."
        },
        {
            title: "Group Technology Pin of Excellence",
            org: "Maybank",
            detail: "Awarded for outstanding performance during the Nov 2024 to Jan 2025 quarter."
        }
    ],

    contact: {
        title: "Let's build dependable software that performs well when the stakes are real.",
        copy:
            "Reach out for backend engineering roles, platform work, API-heavy product teams, or opportunities where architecture, performance, and dependable execution all matter.",
        note:
            "To add your GitHub or more project links later, update the empty URLs in this file."
    }
};

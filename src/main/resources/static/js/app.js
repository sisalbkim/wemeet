document.addEventListener("DOMContentLoaded", () => {
    const initializeRecommendationMap = () => {
        const mapElement = document.querySelector("#recommendationMap");
        const pointsRoot = document.querySelector("#recommendationMapData");
        const mapErrorElement = document.querySelector("#recommendationMapError");
        if (!mapElement || !pointsRoot || typeof window.kakao?.maps === "undefined") {
            if (mapErrorElement) {
                mapErrorElement.hidden = false;
            }
            return;
        }

        if (mapErrorElement) {
            mapErrorElement.hidden = true;
        }

        const points = Array.from(pointsRoot.querySelectorAll("[data-map-point]"))
            .map((node) => ({
                id: node.dataset.id,
                label: node.dataset.label,
                address: node.dataset.address,
                latitude: Number(node.dataset.lat),
                longitude: Number(node.dataset.lng),
                markerType: node.dataset.type,
                selected: node.dataset.selected === "true"
            }))
            .filter((point) => Number.isFinite(point.latitude) && Number.isFinite(point.longitude));

        if (points.length === 0) {
            return;
        }

        const toLatLng = (point) => new window.kakao.maps.LatLng(point.latitude, point.longitude);
        const escapeHtml = (value) => String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
        const createMarkerImage = (point) => {
            const iconStyle = iconStyleForPoint(point);
            const markerSize = iconStyle.size;
            const svg = `
                <svg xmlns="http://www.w3.org/2000/svg" width="${markerSize}" height="${markerSize}" viewBox="0 0 ${markerSize} ${markerSize}">
                    <circle cx="${markerSize / 2}" cy="${markerSize / 2}" r="${(markerSize - 4) / 2}" fill="white" opacity="0.92"/>
                    <circle cx="${markerSize / 2}" cy="${markerSize / 2}" r="${(markerSize - 6) / 2}" fill="${iconStyle.color}"/>
                </svg>
            `.trim();
            return new window.kakao.maps.MarkerImage(
                `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`,
                new window.kakao.maps.Size(markerSize, markerSize),
                { offset: new window.kakao.maps.Point(markerSize / 2, markerSize / 2) }
            );
        };

        const iconStyleForPoint = (point) => {
            if (point.markerType === "midpoint") {
                return { color: "#ff4c5e", size: 20 };
            }
            if (point.markerType === "anchor") {
                return { color: "#8f3dff", size: 18 };
            }
            if (point.markerType === "participant") {
                return { color: "#13b983", size: 16 };
            }
            if (point.selected) {
                return { color: "#ff9c5b", size: 18 };
            }
            return { color: "#2f6bff", size: 16 };
        };

        const map = new window.kakao.maps.Map(mapElement, {
            center: toLatLng(points[0]),
            level: 5
        });
        map.setZoomable(false);
        const markersById = new Map();
        const infoWindowsById = new Map();
        const bounds = new window.kakao.maps.LatLngBounds();
        const venueCards = Array.from(document.querySelectorAll("[data-map-target]"));
        let activeInfoWindow = null;

        const openInfoWindow = (pointId) => {
            const marker = markersById.get(pointId);
            const infoWindow = infoWindowsById.get(pointId);
            if (!marker || !infoWindow) {
                return;
            }
            if (activeInfoWindow && activeInfoWindow !== infoWindow) {
                activeInfoWindow.close();
            }
            infoWindow.open(map, marker);
            activeInfoWindow = infoWindow;
        };

        points.forEach((point) => {
            const latLng = toLatLng(point);
            const marker = new window.kakao.maps.Marker({
                map,
                position: latLng,
                zIndex: point.selected ? 140 : 120,
                image: createMarkerImage(point)
            });

            const infoWindow = new window.kakao.maps.InfoWindow({
                content: `<div class="kakao-map-infowindow"><strong>${escapeHtml(point.label)}</strong><div>${escapeHtml(point.address)}</div></div>`,
                removable: false
            });

            window.kakao.maps.event.addListener(marker, "click", () => {
                openInfoWindow(point.id);
            });

            markersById.set(point.id, marker);
            infoWindowsById.set(point.id, infoWindow);
            bounds.extend(latLng);
        });

        if (points.length === 1) {
            map.setCenter(toLatLng(points[0]));
            map.setLevel(4);
        } else {
            map.setBounds(bounds, 36, 36, 36, 36);
        }

        window.addEventListener("resize", () => {
            map.relayout();
            if (points.length > 1) {
                map.setBounds(bounds, 36, 36, 36, 36);
            }
        });

        const focusVenueCard = (targetCard) => {
            venueCards.forEach((card) => {
                card.classList.toggle("is-focused", card === targetCard);
            });
        };

        venueCards.forEach((card) => {
            card.addEventListener("mouseenter", () => {
                openInfoWindow(card.dataset.mapTarget);
            });

            card.addEventListener("click", () => {
                const marker = markersById.get(card.dataset.mapTarget);
                if (!marker) {
                    return;
                }

                const target = marker.getPosition();
                map.panTo(target);
                if (map.getLevel() > 4) {
                    map.setLevel(4);
                }
                openInfoWindow(card.dataset.mapTarget);
                focusVenueCard(card);
                mapElement.scrollIntoView({ behavior: "smooth", block: "center" });
            });
        });
    };

    if (document.querySelector("#recommendationMap")) {
        const tryInitializeRecommendationMap = () => {
            if (typeof window.kakao?.maps === "undefined") {
                initializeRecommendationMap();
                return;
            }

            if (typeof window.kakao.maps.load === "function") {
                window.kakao.maps.load(initializeRecommendationMap);
                return;
            }

            initializeRecommendationMap();
        };

        if (typeof window.kakao?.maps === "undefined") {
            window.addEventListener("load", tryInitializeRecommendationMap, { once: true });
        } else {
            tryInitializeRecommendationMap();
        }
    }

    const loadingOverlay = document.querySelector("#pageLoadingOverlay");
    const loadingOverlayShell = loadingOverlay?.closest(".app-shell");
    const loadingOverlayTopbar = loadingOverlayShell?.querySelector(".topbar");
    const loadingOverlayBottomNav = loadingOverlayShell?.querySelector(".bottom-nav");
    const loadingOverlayDialog = loadingOverlay?.querySelector(".loading-overlay__dialog");

    const syncLoadingOverlayFrame = () => {
        if (!loadingOverlay || !loadingOverlayShell) {
            return;
        }

        const shellRect = loadingOverlayShell.getBoundingClientRect();
        const topbarRect = loadingOverlayTopbar?.getBoundingClientRect();
        const bottomNavRect = loadingOverlayBottomNav?.getBoundingClientRect();

        let visibleTop = Math.max(0, shellRect.top);
        let visibleBottom = Math.min(window.innerHeight, shellRect.bottom);

        if (topbarRect && topbarRect.bottom > visibleTop) {
            visibleTop = Math.max(visibleTop, Math.min(window.innerHeight, topbarRect.bottom));
        }
        if (bottomNavRect && bottomNavRect.top < visibleBottom) {
            visibleBottom = Math.min(visibleBottom, Math.max(0, bottomNavRect.top));
        }

        if (visibleBottom - visibleTop < 180) {
            visibleTop = Math.max(0, shellRect.top);
            visibleBottom = Math.min(window.innerHeight, shellRect.bottom);
        }

        const overlayHeight = Math.max(180, visibleBottom - visibleTop);
        const dialogHeight = loadingOverlayDialog?.getBoundingClientRect().height || 160;
        const dialogOffset = Math.max(20, Math.round((overlayHeight - dialogHeight) / 2) - 24);

        loadingOverlay.style.setProperty("--overlay-top", `${visibleTop}px`);
        loadingOverlay.style.setProperty("--overlay-bottom", `${Math.max(0, window.innerHeight - visibleBottom)}px`);
        loadingOverlay.style.setProperty("--overlay-dialog-offset", `${dialogOffset}px`);
    };

    const showLoadingOverlay = () => {
        if (!loadingOverlay) {
            return;
        }

        loadingOverlay.style.visibility = "hidden";
        loadingOverlay.hidden = false;
        syncLoadingOverlayFrame();
        loadingOverlay.style.visibility = "";
        document.body.classList.add("is-loading");
    };

    const runAfterOverlayPaint = (action) => {
        window.requestAnimationFrame(() => {
            window.requestAnimationFrame(() => {
                window.setTimeout(action, 120);
            });
        });
    };

    if (loadingOverlay && loadingOverlayShell) {
        syncLoadingOverlayFrame();
        window.addEventListener("resize", syncLoadingOverlayFrame);
        window.addEventListener("scroll", syncLoadingOverlayFrame, { passive: true });
        if (window.visualViewport) {
            window.visualViewport.addEventListener("resize", syncLoadingOverlayFrame);
            window.visualViewport.addEventListener("scroll", syncLoadingOverlayFrame);
        }
    }

    document.querySelectorAll("[data-loading-overlay]").forEach((link) => {
        link.addEventListener("click", (event) => {
            if (!(link instanceof HTMLAnchorElement)) {
                return;
            }
            if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
                return;
            }

            event.preventDefault();
            showLoadingOverlay();
            runAfterOverlayPaint(() => {
                window.location.href = link.href;
            });
        });
    });

    document.querySelectorAll("form").forEach((form) => {
        form.addEventListener("submit", (event) => {
            const submitter = event.submitter;
            if (!(submitter instanceof HTMLElement) || !submitter.matches("[data-loading-overlay-submit]")) {
                return;
            }

            if (form.dataset.loadingOverlaySubmitting === "true") {
                delete form.dataset.loadingOverlaySubmitting;
                return;
            }

            event.preventDefault();
            form.dataset.loadingOverlaySubmitting = "true";
            showLoadingOverlay();
            runAfterOverlayPaint(() => {
                form.requestSubmit(submitter);
            });
        });
    });

    document.querySelectorAll(".radio-grid").forEach((radioGrid) => {
        const syncChoiceChipState = () => {
            radioGrid.querySelectorAll(".choice-chip").forEach((chip) => {
                const input = chip.querySelector('input[type="radio"]');
                chip.classList.toggle("is-selected", Boolean(input?.checked));
            });
        };

        radioGrid.addEventListener("change", (event) => {
            const target = event.target;
            if (!(target instanceof HTMLInputElement) || target.type !== "radio") {
                return;
            }
            syncChoiceChipState();
        });

        syncChoiceChipState();
    });

    const anchorSelect = document.querySelector("#anchorId");
    const anchorPanel = document.querySelector("[data-anchor-panel]");
    const modeInputs = Array.from(document.querySelectorAll('input[name="mode"]'));
    if (anchorSelect && modeInputs.length > 0) {
        const syncAnchorState = () => {
            const selectedMode = modeInputs.find((input) => input.checked)?.value;
            const anchorEnabled = selectedMode === "ANCHOR";
            anchorSelect.disabled = !anchorEnabled;
            if (anchorPanel) {
                anchorPanel.hidden = !anchorEnabled;
            }
        };

        modeInputs.forEach((input) => {
            input.addEventListener("change", syncAnchorState);
        });

        syncAnchorState();
    }

    document.querySelectorAll("[data-copy-target]").forEach((button) => {
        button.addEventListener("click", async () => {
            const target = document.querySelector(button.dataset.copyTarget);
            const text = target?.textContent?.trim();

            if (!text || !navigator.clipboard) {
                return;
            }

            try {
                await navigator.clipboard.writeText(text);
                const originalText = button.textContent;
                button.textContent = "복사됨";
                window.setTimeout(() => {
                    button.textContent = originalText;
                }, 1200);
            } catch (error) {
                console.warn("Clipboard copy failed", error);
            }
        });
    });
});

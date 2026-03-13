document.addEventListener("DOMContentLoaded", () => {
    const initializeRecommendationMap = () => {
        const mapElement = document.querySelector("#recommendationMap");
        const pointsRoot = document.querySelector("#recommendationMapData");
        if (!mapElement || !pointsRoot || typeof window.L === "undefined") {
            return;
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

        const map = window.L.map(mapElement, {
            scrollWheelZoom: false
        });

        window.L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
            attribution: "&copy; OpenStreetMap contributors"
        }).addTo(map);

        const markersById = new Map();
        const bounds = [];

        const styleForPoint = (point) => {
            if (point.markerType === "midpoint") {
                return { color: "#ff4c5e", fillColor: "#ff4c5e", radius: 10 };
            }
            if (point.markerType === "anchor") {
                return { color: "#8f3dff", fillColor: "#8f3dff", radius: 9 };
            }
            if (point.markerType === "participant") {
                return { color: "#13b983", fillColor: "#13b983", radius: 8 };
            }
            if (point.selected) {
                return { color: "#ff9c5b", fillColor: "#ff9c5b", radius: 9 };
            }
            return { color: "#2f6bff", fillColor: "#2f6bff", radius: 8 };
        };

        points.forEach((point) => {
            const marker = window.L.circleMarker([point.latitude, point.longitude], {
                ...styleForPoint(point),
                weight: 2,
                fillOpacity: 0.9
            }).addTo(map);

            marker.bindPopup(`<strong>${point.label}</strong><div>${point.address}</div>`);
            markersById.set(point.id, marker);
            bounds.push([point.latitude, point.longitude]);
        });

        map.fitBounds(bounds, { padding: [24, 24] });

        document.querySelectorAll("[data-map-target]").forEach((card) => {
            card.addEventListener("mouseenter", () => {
                const marker = markersById.get(card.dataset.mapTarget);
                if (marker) {
                    marker.openPopup();
                }
            });
        });
    };

    if (document.querySelector("#recommendationMap")) {
        if (typeof window.L === "undefined") {
            window.addEventListener("load", initializeRecommendationMap, { once: true });
        } else {
            initializeRecommendationMap();
        }
    }

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

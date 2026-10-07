// 추천 결과 지도와 경로 패널 전용 스크립트다.
document.addEventListener("DOMContentLoaded", () => {
    // 추천 결과 화면의 지도와 장소 카드 패널을 동기화한다.
    const initializeRecommendationMap = () => {
        const mapElement = document.querySelector("#recommendationMap");
        const pointsRoot = document.querySelector("#recommendationMapData");
        const mapErrorElement = document.querySelector("#recommendationMapError");
        if (!mapElement || !pointsRoot || typeof window.naver?.maps === "undefined") {
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

        const toLatLng = (point) => new window.naver.maps.LatLng(point.latitude, point.longitude);
        const escapeHtml = (value) => String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
        const markerCaptionForPoint = (point) => {
            if (point.markerType === "midpoint") {
                return "중심";
            }
            if (point.markerType === "anchor") {
                return "기준";
            }
            if (point.markerType === "participant") {
                return "출발";
            }
            if (point.selected) {
                return "추천";
            }
            return "장소";
        };

        const map = new window.naver.maps.Map(mapElement, {
            center: toLatLng(points[0]),
            zoom: 15
        });
        const pointsById = new Map(points.map((point) => [point.id, point]));
        const markersById = new Map();
        const infoWindowsById = new Map();
        const bounds = new window.naver.maps.LatLngBounds();
        let venueCards = Array.from(document.querySelectorAll("[data-map-target]"));
        const venuePanel = document.querySelector("#recommendationVenuePanel");
        const venuePanelEyebrow = document.querySelector("#recommendationVenuePanelEyebrow");
        const venuePanelName = document.querySelector("#recommendationVenuePanelName");
        const venuePanelAverage = document.querySelector("#recommendationVenuePanelAverage");
        const heroAverage = document.querySelector("#recommendationHeroAverage");
        const heroFairness = document.querySelector("#recommendationHeroFairness");
        const venuePanelDescription = document.querySelector("#recommendationVenuePanelDescription");
        const venuePanelTelephone = document.querySelector("#recommendationVenuePanelTelephone");
        const venuePanelCallButton = document.querySelector("#recommendationVenuePanelCallButton");
        const venuePanelLink = document.querySelector("#recommendationVenuePanelLink");
        const favoritePlaceName = document.querySelector("#favoritePlaceName");
        const favoritePlaceCategory = document.querySelector("#favoritePlaceCategory");
        const favoritePlaceAddress = document.querySelector("#favoritePlaceAddress");
        const favoritePlaceLatitude = document.querySelector("#favoritePlaceLatitude");
        const favoritePlaceLongitude = document.querySelector("#favoritePlaceLongitude");

        const favoriteForm = document.querySelector("#recommendationFavoriteForm");
        const favoriteButton = document.querySelector("#recommendationFavoriteButton");
        if (favoriteForm && favoriteButton) {
            favoriteForm.addEventListener("submit", async (event) => {
                event.preventDefault(); // /favorites 페이지로 이동하는 것 방지

                const formData = new FormData(favoriteForm);

                try {
                    const response = await fetch(favoriteForm.action, {
                        method: "POST",
                        body: formData
                    });

                    if (!response.ok) {
                        throw new Error("즐겨찾기 요청 실패");
                    }

                    const isFavorite = await response.json();

                    if (isFavorite) {
                        favoriteButton.textContent = "★ 즐겨찾기";
                    } else {
                        favoriteButton.textContent = "☆ 즐겨찾기";
                    }

                } catch (error) {
                    console.error(error);
                }
            });
        }

        async function updateFavoriteButton(latitude, longitude) {
            if (!favoriteButton || !latitude || !longitude) {
                return;
            }

            try {
                const params = new URLSearchParams({
                    latitude: latitude,
                    longitude: longitude
                });

                const response = await fetch(`/favorites/check?${params}`);

                if (!response.ok) {
                    throw new Error("즐겨찾기 상태 확인 실패");
                }

                const isFavorite = await response.json();

                favoriteButton.textContent =
                    isFavorite ? "★ 즐겨찾기" : "☆ 즐겨찾기";

            } catch (error) {
                console.error(error);
            }
        }
        const venuePanelReason = document.querySelector("#recommendationVenuePanelReason");
        const venuePanelFairness = document.querySelector("#recommendationVenuePanelFairness");
        const venuePanelHighlights = document.querySelector("#recommendationVenuePanelHighlights");
        const venuePanelTravelTimes = document.querySelector("#recommendationVenuePanelTravelTimes");
        const meetingPlaceNameInput = document.querySelector("[data-meeting-place-name-input]");
        const meetingPlaceAddressInput = document.querySelector("[data-meeting-place-address-input]");
        const meetingPlaceNameText = document.querySelector("[data-meeting-place-name]");
        const meetingPlaceAddressText = document.querySelector("[data-meeting-place-address]");
        const routeToggleButton = document.querySelector("#recommendationRouteToggle");
        const routeModeButtons = Array.from(document.querySelectorAll("[data-route-mode-button]"));
        const venueCardsByPointId = new Map(venueCards.map((card) => [card.dataset.mapTarget, card]));
        const mapSection = mapElement.closest(".panel");
        const routePalette = ["#f97316", "#16a34a", "#2563eb", "#0891b2", "#dc2626", "#7c3aed"];
        const participantColorById = new Map();
        const participantRouteCount = Math.max(0, ...venueCards.map((card) => card.querySelectorAll("[data-venue-route][data-route-mode='car']").length));
        let routesVisible = participantRouteCount < 6;
        const initialRouteMode = mapElement.dataset.initialRouteMode || "car";
        let activeRouteMode = ["car", "transit", "walk"].includes(initialRouteMode) ? initialRouteMode : "car";
        let activeRouteLines = [];
        let activeInfoWindow = null;
        let activeInfoWindowPointId = null;
        let activeVenueCard = null;
        let venuePanelAnimationToken = 0;
        const currentUserPointId = mapElement.dataset.currentUserId || "";

        const buildNaverSearchUrl = (searchQuery) => searchQuery
            ? `https://map.naver.com/p/search/${encodeURIComponent(searchQuery)}`
            : "#";
        const buildNaverDirectionsUrl = (originPoint, card) => {
            const destinationName = card?.dataset.venueName || card?.dataset.venueSearchQuery || "";
            const destinationLat = Number(card?.dataset.venueLatitude);
            const destinationLng = Number(card?.dataset.venueLongitude);

            if (!originPoint
                || !destinationName
                || !Number.isFinite(originPoint.latitude)
                || !Number.isFinite(originPoint.longitude)
                || !Number.isFinite(destinationLat)
                || !Number.isFinite(destinationLng)) {
                return buildNaverSearchUrl(destinationName);
            }

            const params = new URLSearchParams({
                menu: "route",
                slng: String(originPoint.longitude),
                slat: String(originPoint.latitude),
                stext: originPoint.address || originPoint.label || "출발지",
                elng: String(destinationLng),
                elat: String(destinationLat),
                etext: destinationName
            });
            return `https://map.naver.com/index.nhn?${params.toString()}`;
        };

        const readRoutePath = (routeNode) => Array.from(routeNode.querySelectorAll("[data-route-point]"))
            .map((node) => ({
                latitude: Number(node.dataset.lat),
                longitude: Number(node.dataset.lng)
            }))
            .filter((point) => Number.isFinite(point.latitude) && Number.isFinite(point.longitude))
            .map((point) => new window.naver.maps.LatLng(point.latitude, point.longitude));

        const buildFallbackRoutePath = (routeNode, card) => {
            const originPoint = pointsById.get(routeNode.dataset.participantId);
            const destinationPoint = pointsById.get(card.dataset.mapTarget);
            if (!originPoint || !destinationPoint) {
                return [];
            }
            return [toLatLng(originPoint), toLatLng(destinationPoint)];
        };

        const routeNodesForColorReference = venueCards
            .map((card) => Array.from(card.querySelectorAll("[data-venue-route]")))
            .find((routeNodes) => routeNodes.length > 0) ?? [];

        routeNodesForColorReference.forEach((routeNode, index) => {
            const participantId = routeNode.dataset.participantId ?? "";
            if (!participantId || participantColorById.has(participantId)) {
                return;
            }
            participantColorById.set(participantId, routePalette[index % routePalette.length]);
        });

        const markerColorForPoint = (point) => {
            if (point.markerType === "participant" || point.markerType === "anchor") {
                return participantColorById.get(point.id) ?? routePalette[0];
            }
            return null;
        };

        const buildOriginMarkerIcon = (point) => {
            const color = markerColorForPoint(point);
            if (!color) {
                return null;
            }

            return {
                content: `
                    <div class="map-origin-marker" style="--origin-marker-color: ${escapeHtml(color)};" aria-hidden="true">
                        <span class="map-origin-marker__dot"></span>
                    </div>
                `,
                size: new window.naver.maps.Size(18, 18),
                anchor: new window.naver.maps.Point(9, 9)
            };
        };

        const clearRouteLines = () => {
            activeRouteLines.forEach((routeLine) => routeLine.setMap(null));
            activeRouteLines = [];
        };

        const hasRenderableRouteForMode = (card, mode) => {
            if (!card) {
                return false;
            }
            return Array.from(card.querySelectorAll(`[data-venue-route][data-route-mode="${mode}"]`))
                .some((routeNode) => {
                    if (routeNode.dataset.routeAvailable === "false") {
                        return false;
                    }
                    const path = readRoutePath(routeNode);
                    if (path.length >= 2) {
                        return true;
                    }
                    return mode !== "transit" && buildFallbackRoutePath(routeNode, card).length >= 2;
                });
        };

        const resolvePreferredVenueCard = (mode) => {
            const activeCard = activeVenueCard ?? venueCards[0] ?? null;
            if (hasRenderableRouteForMode(activeCard, mode)) {
                return activeCard;
            }

            const fallbackCard = venueCards.find((card) => hasRenderableRouteForMode(card, mode));
            return fallbackCard ?? activeCard;
        };

        const syncRouteToggleButton = () => {
            if (!(routeToggleButton instanceof HTMLButtonElement)) {
                return;
            }
            routeToggleButton.textContent = routesVisible ? "경로선 끄기" : "경로선 켜기";
            routeToggleButton.setAttribute("aria-pressed", String(routesVisible));
            routeToggleButton.classList.toggle("is-active", routesVisible);
        };

        const drawRoutesForCard = (card) => {
            clearRouteLines();
            if (!card || !routesVisible) {
                return;
            }

            Array.from(card.querySelectorAll(`[data-venue-route][data-route-mode="${activeRouteMode}"]`))
                .forEach((routeNode, index) => {
                if (activeRouteMode !== "transit" && routeNode.dataset.routeAvailable === "false") {
                    return;
                }

                let path = readRoutePath(routeNode);
                if (path.length < 2 && activeRouteMode !== "transit") {
                    path = buildFallbackRoutePath(routeNode, card);
                }
                if (path.length < 2) {
                    return;
                }

                const routeLine = new window.naver.maps.Polyline({
                    map,
                    path,
                    strokeColor: routePalette[index % routePalette.length],
                    strokeOpacity: 0.86,
                    strokeWeight: 5,
                    strokeStyle: "solid",
                    strokeLineCap: "round",
                    strokeLineJoin: "round",
                    zIndex: 90
                });
                activeRouteLines.push(routeLine);
            });
        };

        const syncRouteModeButtons = () => {
            routeModeButtons.forEach((button) => {
                const active = button.dataset.routeMode === activeRouteMode;
                button.classList.toggle("is-active", active);
                button.setAttribute("aria-pressed", String(active));
            });
        };

        const summarizeVenueTravelTimes = (card) => {
            const entries = Array.from(card.querySelectorAll("[data-venue-time]")).map((node) => {
                const participantId = node.dataset.participantId ?? "";
                const routeNode = card.querySelector(
                    `[data-venue-route][data-participant-id="${CSS.escape(participantId)}"][data-route-mode="${activeRouteMode}"]`
                );
                const minutes = Number(routeNode?.dataset.minutes ?? node.dataset.minutes);
                const available = routeNode?.dataset.routeAvailable !== "false" && Number.isFinite(minutes);
                return {
                    name: node.dataset.name ?? "",
                    available,
                    minutes: available ? minutes : null
                };
            });
            const resolvedMinutes = entries.filter((entry) => Number.isFinite(entry.minutes)).map((entry) => entry.minutes);
            return {
                entries,
                average: resolvedMinutes.length === entries.length && resolvedMinutes.length > 0
                    ? Math.round(resolvedMinutes.reduce((sum, minutes) => sum + minutes, 0) / resolvedMinutes.length)
                    : null,
                fairness: resolvedMinutes.length === entries.length && resolvedMinutes.length > 0
                    ? Math.max(...resolvedMinutes) - Math.min(...resolvedMinutes)
                    : null
            };
        };

        const renderPanelTravelTimes = (card) => {
            if (!venuePanelTravelTimes || !card) {
                return;
            }
            const summary = summarizeVenueTravelTimes(card);
            venuePanelTravelTimes.replaceChildren();
            summary.entries.forEach((entry) => {
                const pill = document.createElement("span");
                pill.className = "participant-pill participant-pill--light";
                pill.textContent = entry.available
                    ? `${entry.name} ${entry.minutes}분`
                    : `${entry.name} 확인 불가`;
                venuePanelTravelTimes.append(pill);
            });
            return summary;
        };

        const renderVenueCardTravelTimes = (card) => {
            const summary = summarizeVenueTravelTimes(card);
            const visiblePills = Array.from(card.querySelectorAll("[data-card-venue-time]"));
            visiblePills.forEach((pill, index) => {
                const entry = summary.entries[index];
                if (!entry) {
                    return;
                }
                pill.textContent = entry.available
                    ? `${entry.name} ${entry.minutes}분`
                    : `${entry.name} 확인 불가`;
            });
            const scoreValue = card.querySelector(".score-badge strong");
            if (scoreValue && summary.fairness !== null) {
                scoreValue.textContent = `${summary.fairness}분`;
            }
            return summary;
        };

        const renderAllVenueCardTravelTimes = () => {
            venueCards.forEach((card) => {
                renderVenueCardTravelTimes(card);
            });
        };

        routeToggleButton?.addEventListener("click", () => {
            routesVisible = !routesVisible;
            syncRouteToggleButton();
            drawRoutesForCard(resolvePreferredVenueCard(activeRouteMode));
        });

        syncRouteToggleButton();
        syncRouteModeButtons();
        renderAllVenueCardTravelTimes();

        routeModeButtons.forEach((button) => {
            button.addEventListener("click", () => {
                activeRouteMode = button.dataset.routeMode || "car";
                syncRouteModeButtons();
                renderAllVenueCardTravelTimes();
                const preferredCard = resolvePreferredVenueCard(activeRouteMode);
                if (preferredCard && preferredCard !== activeVenueCard) {
                    focusVenueCard(preferredCard);
                    fillVenuePanel(preferredCard);
                    openInfoWindow(preferredCard.dataset.mapTarget);
                } else {
                    drawRoutesForCard(preferredCard);
                    applyVenuePanelContent(preferredCard);
                }
            });
        });

        const applyVenuePanelContent = (card) => {
            if (!venuePanel || !card) {
                return;
            }
            const summary = summarizeVenueTravelTimes(card);

            if (venuePanelEyebrow) {
                venuePanelEyebrow.textContent = `${card.dataset.venueArea ?? ""} · ${card.dataset.venueCategory ?? ""}`.trim();
            }
            if (venuePanelName) {
                venuePanelName.textContent = card.dataset.venueName ?? "";
            }
            // 현재 선택된 장소 정보를 즐겨찾기 form에도 반영
            if (favoritePlaceName) {
                favoritePlaceName.value = card.dataset.venueName ?? "";
            }

            if (favoritePlaceCategory) {
                favoritePlaceCategory.value = card.dataset.venueCategory ?? "";
            }

            updateFavoriteButton(
                card.dataset.venueLatitude,
                card.dataset.venueLongitude
            );

            if (favoritePlaceAddress) {
                favoritePlaceAddress.value =
                    card.dataset.venueAddress ??
                    card.dataset.venueDescription ??
                    "";
            }

            if (favoritePlaceLatitude) {
                favoritePlaceLatitude.value = card.dataset.venueLatitude ?? "";
            }

            if (favoritePlaceLongitude) {
                favoritePlaceLongitude.value = card.dataset.venueLongitude ?? "";
            }
            if (venuePanelAverage) {
                venuePanelAverage.textContent = `${summary.average ?? Number(card.dataset.venueAverage ?? 0)}분`;
            }
            if (heroAverage) {
                heroAverage.textContent = `${summary.average ?? Number(card.dataset.venueAverage ?? 0)}분`;
            }
            if (venuePanelDescription) {
                venuePanelDescription.textContent = card.dataset.venueDescription ?? "";
            }
            if (meetingPlaceNameInput instanceof HTMLInputElement) {
                meetingPlaceNameInput.value = card.dataset.venueName ?? "";
            }
            if (meetingPlaceAddressInput instanceof HTMLInputElement) {
                meetingPlaceAddressInput.value = card.dataset.venueAddress ?? card.dataset.venueDescription ?? "";
            }
            if (meetingPlaceNameText) {
                meetingPlaceNameText.textContent = card.dataset.venueName ?? "";
            }
            if (meetingPlaceAddressText) {
                meetingPlaceAddressText.textContent = card.dataset.venueAddress ?? card.dataset.venueDescription ?? "";
            }
            if (venuePanelTelephone) {
                venuePanelTelephone.textContent = card.dataset.venueTelephone || "전화번호 정보 없음";
            }
            if (venuePanelCallButton) {
                const phone = card.dataset.venueTelephone || "";
                venuePanelCallButton.dataset.phone = phone;
                venuePanelCallButton.disabled = false;
            }
            if (venuePanelLink) {
                const originPoint = pointsById.get(currentUserPointId)
                    || points.find((point) => point.markerType === "participant" || point.markerType === "anchor")
                    || null;
                const directionsUrl = buildNaverDirectionsUrl(originPoint, card);
                const linkDisabled = directionsUrl === "#";
                venuePanelLink.href = directionsUrl;
                venuePanelLink.classList.toggle("is-disabled", linkDisabled);
                venuePanelLink.setAttribute("aria-disabled", String(linkDisabled));
                venuePanelLink.textContent = "네이버 지도에서 보기";
            }
            if (venuePanelReason) {
                venuePanelReason.textContent = card.dataset.venueReason ?? "";
            }
            if (venuePanelFairness) {
                venuePanelFairness.textContent = `${summary.fairness ?? Number(card.dataset.venueFairness ?? 0)}분`;
            }
            if (heroFairness) {
                heroFairness.textContent = `${summary.fairness ?? Number(card.dataset.venueFairness ?? 0)}분`;
            }
            if (venuePanelHighlights) {
                venuePanelHighlights.replaceChildren();
                card.querySelectorAll("[data-venue-highlight]").forEach((node) => {
                    const chip = document.createElement("span");
                    chip.className = "mini-tag";
                    chip.textContent = node.textContent ?? "";
                    venuePanelHighlights.append(chip);
                });
            }
            renderPanelTravelTimes(card);
            venuePanel.classList.add("is-active");
        };

        const fillVenuePanel = (card) => {
            if (!venuePanel || !card) {
                return;
            }
            drawRoutesForCard(card);
            if (activeVenueCard === card) {
                applyVenuePanelContent(card);
                return;
            }

            const currentIndex = activeVenueCard ? venueCards.indexOf(activeVenueCard) : 0;
            const nextIndex = venueCards.indexOf(card);
            const direction = nextIndex >= currentIndex ? "forward" : "backward";
            const animationToken = ++venuePanelAnimationToken;

            venuePanel.classList.remove(
                "is-sliding-out-forward",
                "is-sliding-out-backward",
                "is-sliding-in-forward",
                "is-sliding-in-backward"
            );

            if (activeVenueCard && activeVenueCard !== card) {
                venuePanel.classList.add(`is-sliding-out-${direction}`);
                window.setTimeout(() => {
                    if (venuePanelAnimationToken !== animationToken) {
                        return;
                    }
                    applyVenuePanelContent(card);
                    venuePanel.classList.remove(`is-sliding-out-${direction}`);
                    venuePanel.classList.add(`is-sliding-in-${direction}`);
                    window.setTimeout(() => {
                        if (venuePanelAnimationToken !== animationToken) {
                            return;
                        }
                        venuePanel.classList.remove(`is-sliding-in-${direction}`);
                    }, 240);
                }, 140);
            } else {
                applyVenuePanelContent(card);
                venuePanel.classList.add(`is-sliding-in-${direction}`);
                window.setTimeout(() => {
                    if (venuePanelAnimationToken !== animationToken) {
                        return;
                    }
                    venuePanel.classList.remove(`is-sliding-in-${direction}`);
                }, 240);
            }

            activeVenueCard = card;
        };

        const openInfoWindow = (pointId) => {
            const marker = markersById.get(pointId);
            const infoWindow = infoWindowsById.get(pointId);
            if (!marker || !infoWindow) {
                return false;
            }
            if (activeInfoWindow && activeInfoWindow !== infoWindow) {
                activeInfoWindow.close();
            }
            infoWindow.open(map, marker);
            activeInfoWindow = infoWindow;
            activeInfoWindowPointId = pointId;
            return true;
        };

        const toggleInfoWindow = (pointId) => {
            const infoWindow = infoWindowsById.get(pointId);
            if (!infoWindow) {
                return false;
            }
            if (activeInfoWindow === infoWindow && activeInfoWindowPointId === pointId) {
                infoWindow.close();
                activeInfoWindow = null;
                activeInfoWindowPointId = null;
                return false;
            }
            return openInfoWindow(pointId);
        };

        points.forEach((point) => {
            const latLng = toLatLng(point);
            const customIcon = buildOriginMarkerIcon(point);
            const marker = new window.naver.maps.Marker({
                map,
                position: latLng,
                icon: customIcon ?? undefined,
                zIndex: point.selected ? 140 : 120,
                title: point.label
            });

            const infoWindow = new window.naver.maps.InfoWindow({
                content: `<div class="map-infowindow"><strong>${escapeHtml(point.label)}</strong><div>${escapeHtml(point.address)}</div><span>${escapeHtml(markerCaptionForPoint(point))}</span></div>`,
                borderWidth: 0,
                disableAnchor: false,
                backgroundColor: "transparent"
            });

            window.naver.maps.Event.addListener(marker, "click", () => {
                const infoWindowOpened = toggleInfoWindow(point.id);
                const targetCard = venueCardsByPointId.get(point.id);
                if (infoWindowOpened && targetCard) {
                    focusVenueCard(targetCard);
                    fillVenuePanel(targetCard);
                }
            });

            markersById.set(point.id, marker);
            infoWindowsById.set(point.id, infoWindow);
            bounds.extend(latLng);
        });

        const fitMapToPoints = () => {
            if (points.length === 1) {
                map.setCenter(toLatLng(points[0]));
                map.setZoom(16);
                return;
            }
            map.fitBounds(bounds);
        };

        fitMapToPoints();

        window.addEventListener("resize", () => {
            fitMapToPoints();
        });

        const focusVenueCard = (targetCard) => {
            venueCards.forEach((card) => {
                card.classList.toggle("is-focused", card === targetCard);
            });
        };

        window.registerRecommendationVenue = (card) => {
            if (!card) {
                return;
            }

            const pointId = `venue-${venueCards.length}`;

            card.dataset.mapTarget = pointId;
            venueCards.push(card);
            venueCardsByPointId.set(pointId, card);

            const point = {
                id: pointId,
                label: card.dataset.venueName || "",
                address: card.dataset.venueDescription || "",
                latitude: Number(card.dataset.venueLatitude),
                longitude: Number(card.dataset.venueLongitude),
                markerType: "venue",
                selected: false
            };

            if (!Number.isFinite(point.latitude) || !Number.isFinite(point.longitude)) {
                return;
            }

            points.push(point);
            pointsById.set(pointId, point);

            const latLng = toLatLng(point);

            const marker = new window.naver.maps.Marker({
                map,
                position: latLng,
                zIndex: 120,
                title: point.label
            });

            const infoWindow = new window.naver.maps.InfoWindow({
                content: `
            <div class="map-infowindow">
                <strong>${escapeHtml(point.label)}</strong>
                <div>${escapeHtml(point.address)}</div>
                <span>장소</span>
            </div>
        `,
                borderWidth: 0,
                disableAnchor: false,
                backgroundColor: "transparent"
            });

            markersById.set(pointId, marker);
            infoWindowsById.set(pointId, infoWindow);
            bounds.extend(latLng);

            window.naver.maps.Event.addListener(marker, "click", () => {
                openInfoWindow(pointId);
                focusVenueCard(card);
                fillVenuePanel(card);
            });

            card.addEventListener("mouseenter", () => {
                openInfoWindow(pointId);
            });

            card.addEventListener("click", () => {
                map.panTo(latLng);

                if (typeof map.getZoom === "function" && map.getZoom() > 16) {
                    map.setZoom(16);
                }

                openInfoWindow(pointId);
                focusVenueCard(card);
                fillVenuePanel(card);

                mapSection?.scrollIntoView({
                    behavior: "smooth",
                    block: "start"
                });
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
                if (typeof map.getZoom === "function" && map.getZoom() > 16) {
                    map.setZoom(16);
                }
                openInfoWindow(card.dataset.mapTarget);
                focusVenueCard(card);
                fillVenuePanel(card);
                mapSection?.scrollIntoView({ behavior: "smooth", block: "start" });
            });
        });

        if (venueCards.length > 0) {
            const preferredCard = resolvePreferredVenueCard(activeRouteMode);
            focusVenueCard(preferredCard);
            fillVenuePanel(preferredCard);
            openInfoWindow(preferredCard.dataset.mapTarget);
        }

        venuePanelCallButton?.addEventListener("click", (event) => {
            event.preventDefault();
        });

        venuePanelLink?.addEventListener("click", (event) => {
            if (venuePanelLink.classList.contains("is-disabled")) {
                event.preventDefault();
                return;
            }

            event.preventDefault();
            window.open(venuePanelLink.href, "_blank", "noopener,noreferrer");
        });
    };

    if (document.querySelector("#recommendationMap")) {
        const tryInitializeRecommendationMap = () => {
            if (typeof window.naver?.maps === "undefined") {
                initializeRecommendationMap();
                return;
            }

            initializeRecommendationMap();
        };

        if (typeof window.naver?.maps === "undefined") {
            window.addEventListener("load", tryInitializeRecommendationMap, { once: true });
        } else {
            tryInitializeRecommendationMap();
        }
    }
});

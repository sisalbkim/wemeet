document.addEventListener("DOMContentLoaded", () => {
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
        const markersById = new Map();
        const infoWindowsById = new Map();
        const bounds = new window.naver.maps.LatLngBounds();
        const venueCards = Array.from(document.querySelectorAll("[data-map-target]"));
        const venuePanel = document.querySelector("#recommendationVenuePanel");
        const venuePanelEyebrow = document.querySelector("#recommendationVenuePanelEyebrow");
        const venuePanelName = document.querySelector("#recommendationVenuePanelName");
        const venuePanelAverage = document.querySelector("#recommendationVenuePanelAverage");
        const venuePanelDescription = document.querySelector("#recommendationVenuePanelDescription");
        const venuePanelTelephone = document.querySelector("#recommendationVenuePanelTelephone");
        const venuePanelCallButton = document.querySelector("#recommendationVenuePanelCallButton");
        const venuePanelLink = document.querySelector("#recommendationVenuePanelLink");
        const venuePanelReason = document.querySelector("#recommendationVenuePanelReason");
        const venuePanelFairness = document.querySelector("#recommendationVenuePanelFairness");
        const venuePanelHighlights = document.querySelector("#recommendationVenuePanelHighlights");
        const venuePanelTravelTimes = document.querySelector("#recommendationVenuePanelTravelTimes");
        const venueCardsByPointId = new Map(venueCards.map((card) => [card.dataset.mapTarget, card]));
        const mapSection = mapElement.closest(".panel");
        let activeInfoWindow = null;
        let activeVenueCard = null;
        let venuePanelAnimationToken = 0;
        let activeVenueSearchQuery = "";

        const buildNaverSearchUrl = (searchQuery) => searchQuery
            ? `https://map.naver.com/p/search/${encodeURIComponent(searchQuery)}`
            : "#";

        const applyVenuePanelContent = (card) => {
            if (!venuePanel || !card) {
                return;
            }

            if (venuePanelEyebrow) {
                venuePanelEyebrow.textContent = `${card.dataset.venueArea ?? ""} · ${card.dataset.venueCategory ?? ""}`.trim();
            }
            if (venuePanelName) {
                venuePanelName.textContent = card.dataset.venueName ?? "";
            }
            if (venuePanelAverage) {
                venuePanelAverage.textContent = `${card.dataset.venueAverage ?? "0"}분`;
            }
            if (venuePanelDescription) {
                venuePanelDescription.textContent = card.dataset.venueDescription ?? "";
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
                const searchQuery = card.dataset.venueSearchQuery || "";
                activeVenueSearchQuery = searchQuery;
                venuePanelLink.href = buildNaverSearchUrl(searchQuery);
                venuePanelLink.classList.toggle("is-disabled", !searchQuery);
                venuePanelLink.setAttribute("aria-disabled", String(!searchQuery));
                venuePanelLink.textContent = "네이버 지도에서 보기";
            }
            if (venuePanelReason) {
                venuePanelReason.textContent = card.dataset.venueReason ?? "";
            }
            if (venuePanelFairness) {
                venuePanelFairness.textContent = `${card.dataset.venueFairness ?? "0"}분`;
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
            if (venuePanelTravelTimes) {
                venuePanelTravelTimes.replaceChildren();
                card.querySelectorAll("[data-venue-time]").forEach((node) => {
                    const pill = document.createElement("span");
                    pill.className = "participant-pill participant-pill--light";
                    pill.textContent = `${node.dataset.name ?? ""} ${node.dataset.minutes ?? "0"}분`;
                    venuePanelTravelTimes.append(pill);
                });
            }
            venuePanel.classList.add("is-active");
        };

        const fillVenuePanel = (card) => {
            if (!venuePanel || !card) {
                return;
            }
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
            const marker = new window.naver.maps.Marker({
                map,
                position: latLng,
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
                openInfoWindow(point.id);
                const targetCard = venueCardsByPointId.get(point.id);
                if (targetCard) {
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
            focusVenueCard(venueCards[0]);
            fillVenuePanel(venueCards[0]);
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
            window.open(buildNaverSearchUrl(activeVenueSearchQuery), "_blank", "noopener,noreferrer");
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

    const signupForm = document.querySelector("[data-signup-form]");
    if (signupForm instanceof HTMLFormElement) {
        const emailInput = signupForm.querySelector("#email");
        const codeInput = signupForm.querySelector("#emailVerificationCode");
        const emailCheckButton = signupForm.querySelector("[data-email-check-button]");
        const emailSendButton = signupForm.querySelector("[data-email-send-button]");
        const emailVerifyButton = signupForm.querySelector("[data-email-verify-button]");
        const emailCheckFeedback = signupForm.querySelector("[data-email-check-feedback]");
        const emailVerifyFeedback = signupForm.querySelector("[data-email-verify-feedback]");
        const defaultSendButtonText = emailSendButton instanceof HTMLButtonElement ? emailSendButton.textContent : "";

        let verifiedEmail = String(signupForm.dataset.verifiedEmail || "").trim().toLowerCase();

        const setFeedback = (node, tone, message) => {
            if (!(node instanceof HTMLElement)) {
                return;
            }
            node.textContent = message;
            node.classList.remove("is-success", "is-error");
            if (tone) {
                node.classList.add(tone === "success" ? "is-success" : "is-error");
            }
        };

        const localizeSignupMessage = (message) => {
            const normalizedMessage = String(message || "").trim();
            const messageMap = {
                "email already exists": "이미 사용 중인 이메일입니다.",
                "email is required": "이메일을 입력해주세요.",
                "invalid email format": "올바른 이메일 형식을 입력해주세요.",
                "먼저 해당 이메일로 인증코드를 전송해주세요.": "먼저 이메일 중복확인을 하고 인증코드를 전송해주세요."
            };
            return messageMap[normalizedMessage] || normalizedMessage || "요청을 처리하지 못했습니다.";
        };

        const currentEmail = () => emailInput instanceof HTMLInputElement ? emailInput.value.trim() : "";

        const validateEmailInput = () => {
            if (!(emailInput instanceof HTMLInputElement)) {
                return false;
            }
            if (!emailInput.reportValidity()) {
                return false;
            }
            return true;
        };

        const clearVerificationState = () => {
            verifiedEmail = "";
            if (emailSendButton instanceof HTMLButtonElement) {
                emailSendButton.textContent = defaultSendButtonText || "이메일 전송";
            }
            setFeedback(emailVerifyFeedback, "", "이메일 인증이 필요합니다.");
        };

        if (emailInput instanceof HTMLInputElement) {
            emailInput.addEventListener("input", () => {
                clearVerificationState();
                setFeedback(emailCheckFeedback, "", "이메일 중복 여부를 먼저 확인해주세요.");
            });
        }

        if (emailCheckButton instanceof HTMLButtonElement) {
            emailCheckButton.addEventListener("click", async () => {
                if (!validateEmailInput()) {
                    return;
                }
                const email = currentEmail();
                try {
                    const response = await fetch(`/api/auth/email/available?email=${encodeURIComponent(email)}`);
                    const result = await response.json();
                    setFeedback(
                        emailCheckFeedback,
                        result.available ? "success" : "error",
                        localizeSignupMessage(result.message)
                    );
                } catch (error) {
                    console.warn("Email availability check failed", error);
                    setFeedback(emailCheckFeedback, "error", "이메일 중복확인 중 오류가 발생했습니다.");
                }
            });
        }

        if (emailSendButton instanceof HTMLButtonElement) {
            emailSendButton.addEventListener("click", async () => {
                if (!validateEmailInput()) {
                    return;
                }
                const email = currentEmail();
                try {
                    const response = await fetch("/api/auth/email/send-code", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ email })
                    });
                    const result = await response.json();
                    setFeedback(
                        emailVerifyFeedback,
                        result.sent ? "success" : "error",
                        result.sent
                            ? `${result.message} 인증코드: ${result.codePreview}`
                            : localizeSignupMessage(result.message)
                    );
                    if (result.sent) {
                        emailSendButton.textContent = "이메일 재전송";
                    }
                    verifiedEmail = "";
                } catch (error) {
                    console.warn("Email verification send failed", error);
                    setFeedback(emailVerifyFeedback, "error", "인증코드 전송 중 오류가 발생했습니다.");
                }
            });
        }

        if (emailVerifyButton instanceof HTMLButtonElement) {
            emailVerifyButton.addEventListener("click", async () => {
                if (!validateEmailInput()) {
                    return;
                }
                const email = currentEmail();
                const code = codeInput instanceof HTMLInputElement ? codeInput.value.trim() : "";
                if (!code) {
                    setFeedback(emailVerifyFeedback, "error", "이메일과 인증코드를 모두 입력해주세요.");
                    return;
                }
                try {
                    const response = await fetch("/api/auth/email/verify-code", {
                        method: "POST",
                        headers: { "Content-Type": "application/json" },
                        body: JSON.stringify({ email, code })
                    });
                    const result = await response.json();
                    if (result.verified) {
                        verifiedEmail = email.toLowerCase();
                    }
                    setFeedback(
                        emailVerifyFeedback,
                        result.verified ? "success" : "error",
                        localizeSignupMessage(result.message)
                    );
                } catch (error) {
                    console.warn("Email verification confirm failed", error);
                    setFeedback(emailVerifyFeedback, "error", "이메일 인증 확인 중 오류가 발생했습니다.");
                }
            });
        }

        if (verifiedEmail && verifiedEmail === currentEmail().toLowerCase()) {
            setFeedback(emailVerifyFeedback, "success", "이메일 인증이 완료된 상태입니다.");
        }

        signupForm.addEventListener("submit", (event) => {
            if (verifiedEmail !== currentEmail().toLowerCase()) {
                event.preventDefault();
                setFeedback(emailVerifyFeedback, "error", "이메일 중복확인과 이메일 인증을 먼저 완료해주세요.");
            }
        });
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

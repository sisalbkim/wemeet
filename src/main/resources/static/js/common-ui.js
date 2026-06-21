// 공통 화면 인터랙션을 담당한다.
document.addEventListener("DOMContentLoaded", () => {
    const appShell = document.querySelector(".app-shell");
    if (appShell instanceof HTMLElement) {
        const currentViewMode = appShell.dataset.viewMode === "desktop" ? "desktop" : "mobile";
        const currentLocation = `${window.location.pathname}${window.location.search}${window.location.hash}`;
        const mobileViewHref = `/view-mode?mode=mobile&redirect=${encodeURIComponent(currentLocation)}`;
        const desktopViewHref = `/view-mode?mode=desktop&redirect=${encodeURIComponent(currentLocation)}`;
        const switcher = document.createElement("div");

        switcher.className = "view-mode-switcher";
        switcher.innerHTML = `
            <span class="view-mode-switcher__label">보기 모드</span>
            <div class="view-mode-switcher__actions">
                <a class="view-mode-switcher__button${currentViewMode === "mobile" ? " is-active" : ""}" href="${mobileViewHref}" data-loading-overlay>모바일로 보기</a>
                <a class="view-mode-switcher__button${currentViewMode === "desktop" ? " is-active" : ""}" href="${desktopViewHref}" data-loading-overlay>웹으로 보기</a>
            </div>
        `;
        const bottomNav = appShell.querySelector(".bottom-nav");
        if (bottomNav instanceof HTMLElement) {
            appShell.insertBefore(switcher, bottomNav);
        } else {
            appShell.appendChild(switcher);
        }
    }

    const signupSuccessModal = document.querySelector("[data-signup-success-modal]");
    if (signupSuccessModal instanceof HTMLElement) {
        const closeSignupSuccessModal = () => {
            signupSuccessModal.hidden = true;
        };

        signupSuccessModal.querySelectorAll("[data-signup-success-close]").forEach((button) => {
            button.addEventListener("click", closeSignupSuccessModal);
        });
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && !signupSuccessModal.hidden) {
                closeSignupSuccessModal();
            }
        });
    }

    // 친구 목록 검색은 화면 안에서 카드 표시만 바꿔 새로고침 없이 처리한다.
    document.querySelectorAll("[data-friend-filter]").forEach((friendFilter) => {
        const filterScope = friendFilter.closest("[data-friend-filter-scope]") ?? document;
        const searchInput = friendFilter.querySelector("[data-friend-search]");
        const searchButton = friendFilter.querySelector("[data-friend-search-submit]");
        const clearButton = friendFilter.querySelector("[data-friend-search-clear]");
        const friendCards = Array.from(filterScope.querySelectorAll("[data-friend-card]"));
        const noResults = filterScope.querySelector("[data-friend-no-results]");
        const countHeading = filterScope.querySelector("[data-friend-count]");

        const normalizeSearchText = (value) => String(value ?? "").trim().toLowerCase();
        const setFriendCount = (visibleCount) => {
            if (!(countHeading instanceof HTMLElement)) {
                return;
            }
            countHeading.textContent = `내 친구 (${visibleCount})`;
        };

        const applyFriendFilter = () => {
            if (!(searchInput instanceof HTMLInputElement)) {
                return;
            }

            const keyword = normalizeSearchText(searchInput.value);
            let visibleCount = 0;
            friendCards.forEach((card) => {
                if (!(card instanceof HTMLElement)) {
                    return;
                }
                const name = normalizeSearchText(card.dataset.friendName);
                const address = normalizeSearchText(card.dataset.friendAddress);
                const matched = !keyword || name.includes(keyword) || address.includes(keyword);
                card.hidden = !matched;
                if (matched) {
                    visibleCount += 1;
                }
            });

            setFriendCount(visibleCount);
            if (noResults instanceof HTMLElement) {
                noResults.hidden = !keyword || visibleCount > 0;
            }
            if (clearButton instanceof HTMLElement) {
                clearButton.hidden = !keyword;
            }
        };

        if (friendFilter instanceof HTMLFormElement) {
            friendFilter.addEventListener("submit", (event) => {
                event.preventDefault();
                applyFriendFilter();
            });
        }

        searchButton?.addEventListener("click", applyFriendFilter);

        clearButton?.addEventListener("click", () => {
            if (searchInput instanceof HTMLInputElement) {
                searchInput.value = "";
                searchInput.focus();
            }
            applyFriendFilter();
        });

        applyFriendFilter();
    });

    // 친구 즐겨찾기는 서버에 저장하되 화면 전체 새로고침 없이 카드 상태만 갱신한다.
    document.querySelectorAll("[data-friend-favorite-form]").forEach((favoriteForm) => {
        if (!(favoriteForm instanceof HTMLFormElement)) {
            return;
        }

        const friendCard = favoriteForm.closest("[data-friend-card]");
        const favoriteInput = favoriteForm.querySelector("[data-friend-favorite-input]");
        const favoriteButton = favoriteForm.querySelector("[data-friend-favorite-button]");
        const favoriteTag = friendCard?.querySelector("[data-friend-favorite-tag]");
        const friendList = friendCard?.closest("[data-friend-list]");

        const reorderFriendCards = () => {
            if (!(friendList instanceof HTMLElement)) {
                return;
            }

            const cards = Array.from(friendList.querySelectorAll("[data-friend-card]"))
                .filter((card) => card instanceof HTMLElement);

            cards.sort((left, right) => {
                const leftFavorite = left.dataset.friendFavorite === "true" ? 0 : 1;
                const rightFavorite = right.dataset.friendFavorite === "true" ? 0 : 1;
                if (leftFavorite !== rightFavorite) {
                    return leftFavorite - rightFavorite;
                }
                return Number(left.dataset.friendIndex ?? 0) - Number(right.dataset.friendIndex ?? 0);
            });

            cards.forEach((card) => friendList.append(card));
        };

        const applyFavoriteState = (favorite) => {
            if (!(friendCard instanceof HTMLElement)
                    || !(favoriteInput instanceof HTMLInputElement)
                    || !(favoriteButton instanceof HTMLButtonElement)) {
                return;
            }

            friendCard.dataset.friendFavorite = String(favorite);
            favoriteInput.value = String(!favorite);
            favoriteButton.classList.toggle("is-active", favorite);
            favoriteButton.textContent = favorite ? "해제" : "즐겨찾기";
            if (favoriteTag instanceof HTMLElement) {
                favoriteTag.hidden = !favorite;
            }
            reorderFriendCards();
        };

        favoriteForm.addEventListener("submit", async (event) => {
            event.preventDefault();
            if (!(favoriteInput instanceof HTMLInputElement) || !(favoriteButton instanceof HTMLButtonElement)) {
                favoriteForm.submit();
                return;
            }

            const nextFavorite = favoriteInput.value === "true";
            favoriteButton.disabled = true;

            try {
                const response = await fetch(favoriteForm.action, {
                    method: favoriteForm.method || "POST",
                    body: new FormData(favoriteForm),
                    credentials: "same-origin"
                });

                if (!response.ok) {
                    throw new Error(`Favorite update failed: ${response.status}`);
                }

                applyFavoriteState(nextFavorite);
            } catch (error) {
                console.warn("Friend favorite update failed", error);
                favoriteForm.submit();
            } finally {
                favoriteButton.disabled = false;
            }
        });
    });

    const friendModal = document.querySelector("[data-friend-modal]");
    const friendModalOpenButton = document.querySelector("[data-friend-modal-open]");
    if (friendModal instanceof HTMLElement && friendModalOpenButton instanceof HTMLElement) {
        const closeButtons = Array.from(friendModal.querySelectorAll("[data-friend-modal-close]"));
        const friendModalShell = friendModal.closest(".app-shell");
        const friendModalTopbar = friendModalShell?.querySelector(".topbar");
        const friendModalBottomNav = friendModalShell?.querySelector(".bottom-nav");
        const friendModalDialog = friendModal.querySelector(".friend-modal__dialog");
        const participantCheckboxes = Array.from(document.querySelectorAll("[data-participant-friend-checkbox]"));
        const modalCheckboxes = Array.from(friendModal.querySelectorAll("[data-friend-modal-checkbox]"));
        let modalReturnFocus = null;

        const participantCheckboxByFriendId = new Map(
            participantCheckboxes
                .filter((checkbox) => checkbox instanceof HTMLInputElement)
                .map((checkbox) => [checkbox.dataset.friendId, checkbox])
        );
        const modalCheckboxesByFriendId = new Map();
        modalCheckboxes
            .filter((checkbox) => checkbox instanceof HTMLInputElement)
            .forEach((checkbox) => {
                const friendId = checkbox.dataset.friendId;
                if (!friendId) {
                    return;
                }
                if (!modalCheckboxesByFriendId.has(friendId)) {
                    modalCheckboxesByFriendId.set(friendId, []);
                }
                modalCheckboxesByFriendId.get(friendId).push(checkbox);
            });

        const syncModalCheckbox = (friendId) => {
            const participantCheckbox = participantCheckboxByFriendId.get(friendId);
            const relatedModalCheckboxes = modalCheckboxesByFriendId.get(friendId) ?? [];
            if (!(participantCheckbox instanceof HTMLInputElement)) {
                return;
            }
            relatedModalCheckboxes.forEach((checkbox) => {
                checkbox.checked = participantCheckbox.checked;
            });
        };

        participantCheckboxes.forEach((checkbox) => {
            if (!(checkbox instanceof HTMLInputElement)) {
                return;
            }
            checkbox.addEventListener("change", () => {
                syncModalCheckbox(checkbox.dataset.friendId);
            });
        });

        modalCheckboxes.forEach((checkbox) => {
            if (!(checkbox instanceof HTMLInputElement)) {
                return;
            }
            checkbox.addEventListener("change", () => {
                const participantCheckbox = participantCheckboxByFriendId.get(checkbox.dataset.friendId);
                if (participantCheckbox instanceof HTMLInputElement) {
                    participantCheckbox.checked = checkbox.checked;
                    participantCheckbox.dispatchEvent(new Event("change", { bubbles: true }));
                }
                syncModalCheckbox(checkbox.dataset.friendId);
            });
        });

        const syncFriendModalFrame = () => {
            if (!(friendModalShell instanceof HTMLElement)) {
                return;
            }

            const shellRect = friendModalShell.getBoundingClientRect();
            const topbarRect = friendModalTopbar?.getBoundingClientRect();
            const bottomNavRect = friendModalBottomNav?.getBoundingClientRect();

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

            const modalTop = Math.max(0, visibleTop - shellRect.top);
            const modalBottom = Math.max(0, shellRect.bottom - visibleBottom);
            const modalHeight = Math.max(180, visibleBottom - visibleTop);
            const dialogHeight = friendModalDialog?.getBoundingClientRect().height || 260;
            const dialogOffset = Math.max(22, Math.round((modalHeight - dialogHeight) / 2));

            friendModal.style.setProperty("--friend-modal-top", `${modalTop}px`);
            friendModal.style.setProperty("--friend-modal-bottom", `${modalBottom}px`);
            friendModal.style.setProperty("--friend-modal-dialog-offset", `${dialogOffset}px`);
        };

        const openFriendModal = () => {
            modalReturnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : friendModalOpenButton;
            participantCheckboxes.forEach((checkbox) => {
                if (checkbox instanceof HTMLInputElement) {
                    syncModalCheckbox(checkbox.dataset.friendId);
                }
            });
            friendModal.style.visibility = "hidden";
            friendModal.hidden = false;
            syncFriendModalFrame();
            friendModal.style.visibility = "";
            document.body.classList.add("is-loading");
            const closeButton = friendModal.querySelector(".friend-modal__close");
            if (closeButton instanceof HTMLElement) {
                closeButton.focus();
            }
        };

        const closeFriendModal = () => {
            friendModal.hidden = true;
            document.body.classList.remove("is-loading");
            if (modalReturnFocus instanceof HTMLElement) {
                modalReturnFocus.focus();
            }
        };

        friendModalOpenButton.addEventListener("click", openFriendModal);
        closeButtons.forEach((button) => {
            button.addEventListener("click", closeFriendModal);
        });
        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape" && !friendModal.hidden) {
                closeFriendModal();
            }
        });
        window.addEventListener("resize", () => {
            if (!friendModal.hidden) {
                syncFriendModalFrame();
            }
        });
        window.addEventListener("scroll", () => {
            if (!friendModal.hidden) {
                syncFriendModalFrame();
            }
        }, { passive: true });
        if (window.visualViewport) {
            window.visualViewport.addEventListener("resize", () => {
                if (!friendModal.hidden) {
                    syncFriendModalFrame();
                }
            });
            window.visualViewport.addEventListener("scroll", () => {
                if (!friendModal.hidden) {
                    syncFriendModalFrame();
                }
            });
        }
    }

    // 페이지 이동/폼 제출 시 로딩 오버레이를 보여 주는 공통 UI 처리다.
    const loadingOverlay = document.querySelector("#pageLoadingOverlay");

    document.querySelectorAll("form[data-confirm-submit]").forEach((form) => {
        form.addEventListener("submit", (event) => {
            if (!(form instanceof HTMLFormElement)) {
                return;
            }
            const message = form.dataset.confirmSubmit;
            if (message && !window.confirm(message)) {
                event.preventDefault();
            }
        });
    });

    const mountLoadingOverlayToBody = () => {
        if (!loadingOverlay || loadingOverlay.parentElement === document.body) {
            return;
        }

        document.body.appendChild(loadingOverlay);
    };

    const showLoadingOverlay = () => {
        if (!loadingOverlay) {
            return;
        }

        mountLoadingOverlayToBody();
        loadingOverlay.hidden = false;
        document.body.classList.add("is-loading");
    };

    const runAfterOverlayPaint = (action) => {
        window.requestAnimationFrame(() => {
            window.requestAnimationFrame(() => {
                window.setTimeout(action, 120);
            });
        });
    };

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

    // 라디오 칩과 anchor 선택 UI 상태를 현재 선택값과 맞춰 준다.
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

    // 화면에 표시된 코드 값을 버튼 한 번으로 복사할 수 있게 한다.
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

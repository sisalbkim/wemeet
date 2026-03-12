document.addEventListener("DOMContentLoaded", () => {
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

(function() {
    function stripReviewFields(value) {
        if (Array.isArray(value)) {
            return value.map(stripReviewFields);
        }
        if (value && typeof value === "object") {
            var output = {};
            Object.keys(value).forEach(function(key) {
                if (key !== "review" && key !== "aggregateRating") {
                    output[key] = stripReviewFields(value[key]);
                }
            });
            return output;
        }
        return value;
    }

    function absoluteHttpUrl(value) {
        if (!value) return null;
        try {
            var url = new URL(value, document.baseURI).href;
            return /^https?:\/\//i.test(url) ? url : null;
        } catch (_) {
            return null;
        }
    }

    function bestSrcsetUrl(srcset) {
        if (!srcset) return null;
        var best = null;
        var bestRank = -1;
        srcset.split(",").forEach(function(entry) {
            var parts = entry.trim().split(/\s+/);
            if (!parts[0]) return;
            var rank = 1;
            if (/^\d+w$/.test(parts[1] || "")) rank = parseInt(parts[1], 10);
            if (/^\d+(?:\.\d+)?x$/.test(parts[1] || "")) rank = parseFloat(parts[1]) * 1000;
            if (rank > bestRank) {
                best = parts[0];
                bestRank = rank;
            }
        });
        return best;
    }

    function coverImageCandidates() {
        var candidates = [];
        document.querySelectorAll("img").forEach(function(image) {
            [image.currentSrc, image.getAttribute("src"), bestSrcsetUrl(image.getAttribute("srcset"))]
                .forEach(function(value) {
                    var url = absoluteHttpUrl(value);
                    if (url && candidates.indexOf(url) < 0 && candidates.length < 5) candidates.push(url);
                });
        });
        return candidates;
    }

    function cleanedBodyHtml() {
        var clone = document.documentElement.cloneNode(true);
        var removeTags = [
            "script", "style", "nav", "header", "footer", "aside", "svg", "iframe", "noscript",
            "video", "button", "form", "input", "select", "option", "textarea", "label",
            "canvas", "picture", "source", "template", "dialog"
        ];
        clone.querySelectorAll(removeTags.join(",")).forEach(function(element) { element.remove(); });

        var comments = [];
        var commentWalker = document.createTreeWalker(clone, NodeFilter.SHOW_COMMENT);
        while (commentWalker.nextNode()) comments.push(commentWalker.currentNode);
        comments.forEach(function(comment) { comment.remove(); });

        clone.querySelectorAll("*").forEach(function(element) {
            Array.from(element.attributes).forEach(function(attribute) {
                var name = attribute.name;
                if (
                    ["class", "style", "id", "srcset", "poster", "autoplay", "controls", "loading", "decoding"]
                        .indexOf(name) >= 0 ||
                    name.indexOf("data-") === 0 || name.indexOf("aria-") === 0 || name.indexOf("on") === 0
                ) {
                    element.removeAttribute(name);
                }
            });
        });

        var whitespace = [];
        var textWalker = document.createTreeWalker(clone, NodeFilter.SHOW_TEXT);
        while (textWalker.nextNode()) {
            if (!textWalker.currentNode.nodeValue || !textWalker.currentNode.nodeValue.trim()) {
                whitespace.push(textWalker.currentNode);
            }
        }
        whitespace.forEach(function(node) { node.remove(); });

        Array.from(clone.querySelectorAll("*")).reverse().forEach(function(element) {
            if (!element.children.length && !(element.textContent || "").trim()) element.remove();
        });
        return clone.body ? clone.body.outerHTML : clone.outerHTML;
    }

    try {
        var jsonLd = [];
        document.querySelectorAll('script[type="application/ld+json"]').forEach(function(script) {
            var text = (script.textContent || "").trim();
            if (!text) return;
            try {
                jsonLd.push(JSON.stringify(stripReviewFields(JSON.parse(text))));
            } catch (_) {
                jsonLd.push(text);
            }
        });

        var metaTags = {};
        ["og:title", "og:image", "og:image:secure_url", "og:description"].forEach(function(name) {
            var tag = document.querySelector('meta[property="' + name + '"]');
            if (tag) metaTags[name] = tag.getAttribute("content") || "";
        });
        [["twitter:image", "twitter:image"], ["description", "description"]].forEach(function(entry) {
            var tag = document.querySelector('meta[name="' + entry[0] + '"]');
            if (tag) metaTags[entry[1]] = tag.getAttribute("content") || "";
        });

        var result = {
            url: window.location.href,
            jsonLd: jsonLd,
            metaTags: metaTags,
            coverImageCandidates: coverImageCandidates(),
            html: cleanedBodyHtml()
        };
        var encoded = JSON.stringify(result);
        if (encoded.length > 750000) {
            result.html = "";
            encoded = JSON.stringify(result);
        }
        if (encoded.length > 750000) {
            return JSON.stringify({
                url: window.location.href,
                jsonLd: [],
                metaTags: metaTags,
                coverImageCandidates: [],
                html: ""
            });
        }
        return encoded;
    } catch (_) {
        return null;
    }
})();

package me.ash.reader.ui.component.webview

object WebViewScript {

    fun get(boldCharacters: Boolean) = """
const BR_WORD_STEM_PERCENTAGE = 0.7;
const MAX_FIXATION_PARTS = 4;
const FIXATION_LOWER_BOUND = 0
function highlightText(sentenceText) {
	return sentenceText.replace(/\p{L}+/gu, (word) => {
		const { length } = word;

		const brWordStemWidth = length > 3 ? Math.round(length * BR_WORD_STEM_PERCENTAGE) : length;

		const firstHalf = word.slice(0, brWordStemWidth);
		const secondHalf = word.slice(brWordStemWidth);
		var htmlWord = "<br-bold>";
        htmlWord += makeFixations(firstHalf);
        htmlWord += "</br-bold>";
        if (secondHalf.length) {
            htmlWord += "<br-edge>";
            htmlWord += makeFixations(secondHalf);
            htmlWord += "</br-edge>";
        }
		return htmlWord;
	});
}

function makeFixations(textContent) {
	const COMPUTED_MAX_FIXATION_PARTS = textContent.length >= MAX_FIXATION_PARTS ? MAX_FIXATION_PARTS : textContent.length;

	const fixationWidth = Math.ceil(textContent.length * (1 / COMPUTED_MAX_FIXATION_PARTS));

	if (fixationWidth === FIXATION_LOWER_BOUND) {
		return '<br-fixation fixation-strength="1">' + textContent + '</br-fixation>';
	}

	const fixationsSplits = new Array(COMPUTED_MAX_FIXATION_PARTS).fill(null).map((item, index) => {
		const wordStartBoundary = index * fixationWidth;
		const wordEndBoundary = wordStartBoundary + fixationWidth > textContent.length ? textContent.length : wordStartBoundary + fixationWidth;

		return `<br-fixation fixation-strength="` + (index + 1) + `">` + textContent.slice(wordStartBoundary, wordEndBoundary) + `</br-fixation>`;
	});

	return fixationsSplits.join('');
}

const IGNORE_NODE_TAGS = ['STYLE', 'SCRIPT', 'BR-SPAN', 'BR-FIXATION', 'BR-BOLD', 'BR-EDGE', 'SVG', 'INPUT', 'TEXTAREA'];
function parseNode(node) {
    if (!node?.parentElement?.tagName || IGNORE_NODE_TAGS.includes(node.parentElement.tagName)) {
        return;
    }
    
    if (node.nodeType === Node.TEXT_NODE && node.nodeValue.length) {
        try {
            const brSpan = document.createElement('br-span');
            brSpan.innerHTML = highlightText(node.nodeValue);
            if (brSpan.childElementCount === 0) return;
            node.parentElement.replaceChild(brSpan, node); // JiffyReader keeps the old element around, but we don't need it
        } catch (e) {
            console.error('Error parsing text node:', e);
        }
        return;
    }
    
    if (node.hasChildNodes()) [...node.childNodes].forEach(parseNode);
}

function setBold(enabled) {
    if (enabled) {
        document.body.setAttribute("br-mode", "on");
        [...document.body.childNodes].forEach(parseNode);
    } else {
        document.body.setAttribute("br-mode", "off");
    }
}

${if (boldCharacters) "setBold(true);" else ""}

// Brief You: follow along with read aloud. Finds the sentence in the article (spaces may differ
// from the read-aloud text) after the last one found, marks it with a CSS highlight (the article
// itself isn't changed), and returns its top on the page, or -1 when it isn't found.
var briefLastEnd = 0;
function briefHighlight(sentence) {
    if (!window.CSS || !CSS.highlights) return -1;
    CSS.highlights.delete("brief-read-aloud");
    if (!sentence) { briefLastEnd = 0; return -1; }
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    var flat = "", map = [], space = true;
    while (walker.nextNode()) {
        var node = walker.currentNode, value = node.nodeValue;
        var parent = node.parentElement;
        if (parent && (parent.tagName === "SCRIPT" || parent.tagName === "STYLE")) continue;
        for (var i = 0; i < value.length; i++) {
            var c = value[i];
            if (/\s/.test(c)) {
                if (space) continue;
                c = " ";
                space = true;
            } else {
                space = false;
            }
            flat += c;
            map.push([node, i]);
        }
    }
    var wanted = sentence.replace(/\s+/g, " ").trim();
    if (!wanted) return -1;
    var at = flat.indexOf(wanted, Math.max(0, briefLastEnd - wanted.length));
    if (at < 0) at = flat.indexOf(wanted);
    if (at < 0) return -1;
    var end = at + wanted.length - 1;
    briefLastEnd = end + 1;
    var range = document.createRange();
    range.setStart(map[at][0], map[at][1]);
    range.setEnd(map[end][0], map[end][1] + 1);
    CSS.highlights.set("brief-read-aloud", new Highlight(range));
    return range.getBoundingClientRect().top + window.scrollY;
}

var images = document.querySelectorAll("img");

images.forEach(function(img) {
    img.onload = function() {
        img.classList.add("loaded");
        console.log("Image width:", img.width, "px");
        if (img.width < 412) {
            img.classList.add("thin");
        }
    };

    // Keep a picture's own fallback (a sharper version that can't load falls back to the
    // normal one, see FeedContent).
    if (!img.onerror) {
        img.onerror = function() {
            console.error("Failed to load image:", img.src);
        };
    }
});
"""
}

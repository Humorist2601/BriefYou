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

// Brief You: read aloud and follow along. briefSentences() lists the article's sentences as shown
// (paragraph by paragraph, in page order); read aloud speaks exactly these, so briefShow(i) marks
// sentence i with a CSS highlight (the article itself isn't changed) and returns its top on the
// page. The list is rebuilt the same way if the page was reloaded.
var briefParts = null;
var BRIEF_BLOCKS = "p,li,h1,h2,h3,h4,h5,h6,blockquote,figcaption,pre,td,th,dd,dt,div,section,article,body";
function briefBuild() {
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
    var paragraphs = [], current = null;
    while (walker.nextNode()) {
        var node = walker.currentNode;
        var parent = node.parentElement;
        if (!parent || parent.closest("script,style,noscript")) continue;
        if (!/\S/.test(node.nodeValue)) continue;
        var block = parent.closest(BRIEF_BLOCKS);
        if (!current || current.block !== block) {
            current = { block: block, flat: "", map: [], space: true };
            paragraphs.push(current);
        }
        var value = node.nodeValue;
        for (var i = 0; i < value.length; i++) {
            var c = value[i];
            if (/\s/.test(c)) {
                if (current.space) continue;
                c = " ";
                current.space = true;
            } else {
                current.space = false;
            }
            current.flat += c;
            current.map.push([node, i]);
        }
    }
    var parts = [];
    var segmenter = (window.Intl && Intl.Segmenter) ? new Intl.Segmenter(undefined, { granularity: "sentence" }) : null;
    paragraphs.forEach(function(p) {
        var spans = [];
        if (segmenter) {
            for (var seg of segmenter.segment(p.flat)) spans.push([seg.index, seg.index + seg.segment.length]);
        } else {
            var re = /[^.!?…]+[.!?…]*["'”’)\]]*\s*/g, m;
            while ((m = re.exec(p.flat)) !== null) spans.push([m.index, m.index + m[0].length]);
        }
        spans.forEach(function(span) {
            var start = span[0], end = span[1];
            while (start < end && p.flat[start] === " ") start++;
            while (end > start && p.flat[end - 1] === " ") end--;
            var text = p.flat.substring(start, end);
            if (!/[\p{L}\p{N}]/u.test(text)) return;
            var range = document.createRange();
            range.setStart(p.map[start][0], p.map[start][1]);
            range.setEnd(p.map[end - 1][0], p.map[end - 1][1] + 1);
            parts.push({ text: text, range: range });
        });
    });
    briefParts = parts;
    return parts;
}
function briefSentences() {
    return briefBuild().map(function(p) { return p.text; });
}
function briefShow(i) {
    if (!window.CSS || !CSS.highlights) return -1;
    CSS.highlights.delete("brief-read-aloud");
    if (i < 0) return -1;
    if (!briefParts) briefBuild();
    var part = briefParts[i];
    if (!part) return -1;
    CSS.highlights.set("brief-read-aloud", new Highlight(part.range));
    return part.range.getBoundingClientRect().top + window.scrollY;
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

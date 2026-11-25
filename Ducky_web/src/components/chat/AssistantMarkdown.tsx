import { memo } from "react";
import Markdown, { type Components } from "react-markdown";
import rehypeKatex from "rehype-katex";
import remarkGfm from "remark-gfm";
import remarkMath from "remark-math";
import "katex/dist/katex.min.css";
import styles from "./AssistantMarkdown.module.css";

const remarkPlugins = [remarkGfm, remarkMath];
const rehypePlugins: [typeof rehypeKatex, { trust: boolean }][] = [[rehypeKatex, { trust: false }]];
const components: Components = {
  table: ({ children }) => (
    <div className={styles.tableScroll}>
      <table>{children}</table>
    </div>
  ),
};

function normalizeMath(markdown: string) {
  if (!/\\[([]/.test(markdown)) return markdown;

  // Stored replies can use LaTeX delimiters that remark-math doesn't recognize.
  const convert = (text: string) => text.replace(
    /(?<!`)(`+)(?!`)[\s\S]*?(?<!`)\1(?!`)|(?<!\\)\\\[([\s\S]*?)\\\]|(?<!\\)\\\(([^\r\n]*?)\\\)/g,
    (whole, ticks: string | undefined, block: string | undefined, inline: string | undefined) => {
      if (ticks) return whole;
      if (block?.trim()) return `\n\n$$\n${block.trim()}\n$$\n\n`;
      if (inline?.trim()) return `$${inline.trim()}$`;
      return whole;
    },
  );

  const output: string[] = [];
  let text = "";
  let openFence: string | undefined;
  for (const line of markdown.split(/(?<=\n)/)) {
    const fence = /^ {0,3}(`{3,}|~{3,})([^\r\n]*)/.exec(line);
    if (openFence) {
      output.push(line);
      if (fence && fence[1][0] === openFence[0] && fence[1].length >= openFence.length && !fence[2].trim()) {
        openFence = undefined;
      }
    } else if (fence || /^( {4}|\t)/.test(line)) {
      output.push(convert(text), line);
      text = "";
      openFence = fence?.[1];
    } else {
      text += line;
    }
  }
  output.push(convert(text));
  return output.join("");
}

export const AssistantMarkdown = memo(function AssistantMarkdown({ content }: { content: string }) {
  return (
    <div className={styles.content}>
      <Markdown
        skipHtml
        disallowedElements={["img"]}
        remarkPlugins={remarkPlugins}
        rehypePlugins={rehypePlugins}
        components={components}
      >
        {normalizeMath(content)}
      </Markdown>
    </div>
  );
});

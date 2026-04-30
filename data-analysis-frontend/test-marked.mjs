import { marked } from 'marked';

// 测试1：标准格式
const text1 = `| a | b |
|---|---|
| 1 | 2 |`;

console.log('=== 测试1：简单表格 ===');
console.log(marked.parse(text1, { async: false }));

// 测试2：带标题
const text2 = `标题

| a | b |
|---|---|
| 1 | 2 |`;

console.log('=== 测试2：带标题 ===');
console.log(marked.parse(text2, { async: false }));

// 测试3：带空格的表格行
const text3 = `标题

 | a | b |
 |---|---|
 | 1 | 2 |`;

console.log('=== 测试3：行首有空格 ===');
console.log(marked.parse(text3, { async: false }));

// 测试4：预处理后的
const preprocess = (t) => t.split('\n').map(l => /^\s*\|/.test(l) ? l.trimStart() : l).join('\n');

console.log('=== 测试4：预处理后 ===');
console.log(marked.parse(preprocess(text3), { async: false }));

// 测试5：检查gfm选项
console.log('=== 测试5：显式gfm ===');
console.log(marked.parse(text1, { async: false, gfm: true }));

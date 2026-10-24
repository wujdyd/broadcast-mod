// 生成广播模组贴图:纯 Node + zlib 手写 PNG 编码器,确定性输出。
'use strict';
const zlib = require('zlib');
const fs = require('fs');
const path = require('path');

const TEX_DIR = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets', 'broadcast', 'textures', 'block');
const RES_DIR = path.join(__dirname, '..', 'src', 'main', 'resources');
fs.mkdirSync(TEX_DIR, { recursive: true });

// ---------- PNG 编码 ----------
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1);
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = 0xFFFFFFFF;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8);
  return (c ^ 0xFFFFFFFF) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function encodePNG(w, h, rgba) {
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 6; // 8bit RGBA
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0; // filter none
    rgba.copy(raw, y * (w * 4 + 1) + 1, y * w * 4, (y + 1) * w * 4);
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  return Buffer.concat([sig, chunk('IHDR', ihdr), chunk('IDAT', idat), chunk('IEND', Buffer.alloc(0))]);
}

// ---------- 画布 ----------
function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
function hex(s) {
  return [parseInt(s.slice(1, 3), 16), parseInt(s.slice(3, 5), 16), parseInt(s.slice(5, 7), 16)];
}
class Canvas {
  constructor(w, h) { this.w = w; this.h = h; this.buf = Buffer.alloc(w * h * 4); }
  set(x, y, c, a) {
    x = Math.floor(x); y = Math.floor(y);
    if (x < 0 || y < 0 || x >= this.w || y >= this.h) return;
    const i = (y * this.w + x) * 4;
    this.buf[i] = c[0]; this.buf[i + 1] = c[1]; this.buf[i + 2] = c[2];
    this.buf[i + 3] = a === undefined ? 255 : a;
  }
  fill(c) { for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) this.set(x, y, c); }
  rect(x0, y0, x1, y1, c) {
    for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) this.set(x, y, c);
  }
  frame(x0, y0, x1, y1, c) {
    for (let x = x0; x <= x1; x++) { this.set(x, y0, c); this.set(x, y1, c); }
    for (let y = y0; y <= y1; y++) { this.set(x0, y, c); this.set(x1, y, c); }
  }
  circle(cx, cy, r, c) {
    for (let y = Math.floor(cy - r); y <= Math.ceil(cy + r); y++)
      for (let x = Math.floor(cx - r); x <= Math.ceil(cx + r); x++)
        if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) this.set(x, y, c);
  }
  ring(cx, cy, r, thick, c) {
    for (let y = Math.floor(cy - r - 1); y <= Math.ceil(cy + r + 1); y++)
      for (let x = Math.floor(cx - r - 1); x <= Math.ceil(cx + r + 1); x++) {
        const d = Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
        if (d <= r && d >= r - thick) this.set(x, y, c);
      }
  }
  noise(rnd, amt) {
    for (let y = 0; y < this.h; y++) for (let x = 0; x < this.w; x++) {
      const i = (y * this.w + x) * 4;
      const d = Math.floor((rnd() - 0.5) * amt);
      this.buf[i] = clamp(this.buf[i] + d);
      this.buf[i + 1] = clamp(this.buf[i + 1] + d);
      this.buf[i + 2] = clamp(this.buf[i + 2] + d);
    }
  }
  save(file) { fs.writeFileSync(file, encodePNG(this.w, this.h, this.buf)); console.log('OK', path.basename(file)); }
}
function clamp(v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
function shade(c, d) { return [clamp(c[0] + d), clamp(c[1] + d), clamp(c[2] + d)]; }

// ---------- 贴图绘制 ----------
const rnd = mulberry32(20261024);

// 1. metal 拉丝金属
{
  const cv = new Canvas(16, 16);
  const base = hex('#8b95a0');
  cv.fill(base);
  for (let y = 0; y < 16; y++) {
    const d = ((y * 37) % 5) - 2 + Math.floor((rnd() - 0.5) * 6);
    for (let x = 0; x < 16; x++) cv.set(x, y, shade(base, d));
  }
  cv.frame(0, 0, 15, 15, hex('#6e7783'));
  cv.set(1, 1, hex('#a7b0ba')); cv.set(14, 14, hex('#5d6672'));
  cv.noise(rnd, 8);
  cv.save(path.join(TEX_DIR, 'metal.png'));
}

// 2. station_side 侧面(金属 + 深色饰带 + 指示灯)
{
  const cv = new Canvas(16, 16);
  const base = hex('#838d99');
  cv.fill(base);
  for (let y = 0; y < 16; y++) {
    const d = ((y * 53) % 4) - 2;
    for (let x = 0; x < 16; x++) cv.set(x, y, shade(base, d));
  }
  cv.rect(0, 5, 15, 8, hex('#39414c'));
  cv.rect(0, 5, 15, 5, hex('#2b323b'));
  cv.circle(3.5, 6.5, 1.2, hex('#f59e0b'));
  cv.circle(12.5, 6.5, 1.2, hex('#3b82f6'));
  cv.frame(0, 0, 15, 15, hex('#5d6672'));
  cv.noise(rnd, 6);
  cv.save(path.join(TEX_DIR, 'station_side.png'));
}

// 3. station_top 顶面(散热格栅)
{
  const cv = new Canvas(16, 16);
  const base = hex('#7e8894');
  cv.fill(base);
  cv.rect(2, 3, 13, 4, hex('#3a424d'));
  cv.rect(2, 7, 13, 8, hex('#3a424d'));
  cv.rect(2, 11, 13, 12, hex('#3a424d'));
  cv.rect(2, 5, 13, 5, hex('#96a0ab'));
  cv.rect(2, 9, 13, 9, hex('#96a0ab'));
  cv.rect(2, 13, 13, 13, hex('#96a0ab'));
  cv.frame(0, 0, 15, 15, hex('#5d6672'));
  cv.noise(rnd, 6);
  cv.save(path.join(TEX_DIR, 'station_top.png'));
}

// 4. station_front 前面板(喇叭网格 + 旋钮 + 指示灯)
{
  const cv = new Canvas(16, 16);
  cv.fill(hex('#2c3440'));
  // 左侧出音孔阵列
  for (let y = 2; y <= 13; y += 2)
    for (let x = 1; x <= 8; x += 2) {
      cv.set(x, y, hex('#161a21'));
      cv.set(x + 1, y + 1, hex('#3d4753'));
    }
  // 右侧旋钮
  cv.circle(11.5, 4.5, 2, hex('#6b7683'));
  cv.ring(11.5, 4.5, 2, 0.6, hex('#39414c'));
  cv.set(11.5, 3, hex('#dbe2ea'));
  cv.circle(11.5, 9.5, 2, hex('#6b7683'));
  cv.ring(11.5, 9.5, 2, 0.6, hex('#39414c'));
  cv.set(12.5, 9, hex('#dbe2ea'));
  // 蓝色工作指示灯
  cv.circle(11.5, 13.5, 1, hex('#60a5fa'));
  cv.ring(11.5, 13.5, 1, 0.5, hex('#1d4ed8'));
  cv.frame(0, 0, 15, 15, hex('#1c232c'));
  cv.noise(rnd, 5);
  cv.save(path.join(TEX_DIR, 'station_front.png'));
}

// 5. grille 喇叭网(深色冲孔)
{
  const cv = new Canvas(16, 16);
  cv.fill(hex('#23272e'));
  for (let y = 1; y < 16; y += 3)
    for (let x = 1; x < 16; x += 3) {
      cv.circle(x + 0.5, y + 0.5, 1, hex('#14171c'));
      cv.set(x + 1, y + 1, hex('#333a44'));
    }
  cv.frame(0, 0, 15, 15, hex('#191d23'));
  cv.noise(rnd, 5);
  cv.save(path.join(TEX_DIR, 'grille.png'));
}

// 6. horn 号筒外壳(校园广播白)
{
  const cv = new Canvas(16, 16);
  const base = hex('#e9e5dc');
  cv.fill(base);
  for (let y = 0; y < 16; y++) {
    const d = Math.floor((rnd() - 0.5) * 8) + (y > 12 ? -10 : 0);
    for (let x = 0; x < 16; x++) cv.set(x, y, shade(base, d));
  }
  cv.rect(0, 14, 15, 15, hex('#c9c3b4'));
  cv.rect(14, 0, 15, 15, hex('#cfc9bd'));
  cv.set(2, 3, hex('#f5f2ea')); cv.set(3, 3, hex('#f5f2ea'));
  cv.frame(0, 0, 15, 15, hex('#b9b3a4'));
  cv.noise(rnd, 5);
  cv.save(path.join(TEX_DIR, 'horn.png'));
}

// 7. inner 号筒内部(同心环)
{
  const cv = new Canvas(16, 16);
  cv.fill(hex('#17191e'));
  cv.ring(8, 8, 6.5, 0.8, hex('#22252c'));
  cv.ring(8, 8, 4.5, 0.8, hex('#1d2026'));
  cv.ring(8, 8, 2.5, 0.8, hex('#262a31'));
  cv.circle(8, 8, 1, hex('#2e333b'));
  cv.noise(rnd, 4);
  cv.save(path.join(TEX_DIR, 'inner.png'));
}

// 8. pole 支杆(深钢)
{
  const cv = new Canvas(16, 16);
  const base = hex('#52595f');
  cv.fill(base);
  for (let x = 0; x < 16; x++) {
    const d = ((x * 29) % 5) - 2;
    for (let y = 0; y < 16; y++) cv.set(x, y, shade(base, d));
  }
  cv.rect(3, 0, 4, 15, hex('#6a727a'));
  cv.rect(12, 0, 13, 15, hex('#3c4247'));
  cv.noise(rnd, 6);
  cv.save(path.join(TEX_DIR, 'pole.png'));
}

// 9. cabinet 音柱箱体(校园蓝 + 横纹)
{
  const cv = new Canvas(16, 16);
  cv.fill(hex('#2f4a63'));
  for (let y = 2; y < 15; y += 3) cv.rect(1, y, 14, y, hex('#3a5a78'));
  cv.frame(0, 0, 15, 15, hex('#22384c'));
  cv.rect(6, 7, 9, 8, hex('#93a1b0')); // 铭牌
  cv.noise(rnd, 6);
  cv.save(path.join(TEX_DIR, 'cabinet.png'));
}

// 10. micmesh 话筒网罩
{
  const cv = new Canvas(16, 16);
  cv.fill(hex('#191b20'));
  for (let y = 0; y < 16; y++)
    for (let x = 0; x < 16; x++) {
      if ((x + y) % 2 === 0) cv.set(x, y, hex('#2b2e36'));
    }
  for (let y = 1; y < 16; y += 4)
    for (let x = 1; x < 16; x += 4) cv.set(x, y, hex('#5a6069'));
  cv.frame(0, 0, 15, 15, hex('#101216'));
  cv.noise(rnd, 4);
  cv.save(path.join(TEX_DIR, 'micmesh.png'));
}

// 11. 模组 logo 64x64
{
  const cv = new Canvas(64, 64);
  cv.fill(hex('#0d1320'));
  cv.circle(32, 32, 26, hex('#16202f'));
  cv.ring(32, 32, 26, 2, hex('#3b82f6'));
  // 喇叭形状
  cv.rect(18, 28, 26, 36, hex('#dbe2ea'));
  for (let i = 0; i < 14; i++) cv.rect(26 + i, 28 - i, 27 + i, 36 + i, hex('#9fb4cc'));
  cv.circle(46, 24, 2.5, hex('#60a5fa'));
  cv.circle(50, 32, 2.5, hex('#60a5fa'));
  cv.circle(46, 40, 2.5, hex('#60a5fa'));
  cv.save(path.join(RES_DIR, 'broadcast_logo.png'));
}

console.log('ALL TEXTURES DONE');

"""Generate an original TV/speech mark. No external artwork or image editing."""
import struct, sys, zlib
size = 256
rows = []
for y in range(size):
    row = bytearray([0])
    for x in range(size):
        color = (19, 35, 55, 255)
        if (42 <= x < 214 and 58 <= y < 188):
            color = (148, 215, 223, 255)
        if (53 <= x < 203 and 69 <= y < 177):
            color = (19, 35, 55, 255)
        if (88 <= x < 168 and 112 <= y < 123) or (88 <= x < 149 and 137 <= y < 148):
            color = (216, 230, 236, 255)
        if 188 <= y < 210 and 70 <= x < 70 + (210-y):
            color = (148, 215, 223, 255)
        row.extend(color)
    rows.append(row)
def chunk(kind, data):
    return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data) & 0xffffffff)
with open(sys.argv[1], 'wb') as out:
    out.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', size, size, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(b''.join(rows))) + chunk(b'IEND', b''))

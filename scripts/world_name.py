"""Edit only Data.LevelName in a gzip NBT save; retain every other byte."""

import gzip
import struct


def rename_world(path, name):
    data = gzip.decompress(path.read_bytes())
    position = 0
    replacement = None

    def take(count):
        nonlocal position
        if count < 0 or position + count > len(data):
            raise ValueError('Truncated or invalid NBT')
        result = data[position:position + count]
        position += count
        return result

    def string():
        return take(struct.unpack('>H', take(2))[0]).decode('utf-8')

    def payload(kind, parents):
        nonlocal replacement
        sizes = {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}
        if kind in sizes:
            take(sizes[kind])
        elif kind in (7, 11, 12):
            count = struct.unpack('>i', take(4))[0]
            take(count * {7: 1, 11: 4, 12: 8}[kind])
        elif kind == 8:
            start = position
            string()
            if parents == ('Data', 'LevelName'):
                if replacement is not None:
                    raise ValueError('Duplicate LevelName')
                replacement = (start, position)
        elif kind == 9:
            element = take(1)[0]
            count = struct.unpack('>i', take(4))[0]
            if count < 0 or count > len(data):
                raise ValueError('Invalid NBT list length')
            for _ in range(count):
                payload(element, parents + ('[]',))
        elif kind == 10:
            while True:
                child = take(1)[0]
                if child == 0:
                    break
                payload(child, parents + (string(),))
        else:
            raise ValueError('Unknown NBT tag: ' + str(kind))

    if take(1) != b'\x0a':
        raise ValueError('NBT root must be a compound')
    string()
    payload(10, ())
    if replacement is None or position != len(data):
        raise ValueError('Missing LevelName or trailing NBT content')
    encoded = name.encode('utf-8')
    start, end = replacement
    path.write_bytes(gzip.compress(data[:start] + struct.pack('>H', len(encoded)) + encoded + data[end:], mtime=0))

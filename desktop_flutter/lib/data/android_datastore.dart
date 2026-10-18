import 'dart:convert';
import 'dart:typed_data';

class AndroidDataStore {
  static Map<String, String> stringPreferences(Uint8List bytes) {
    if (bytes.length > 8 * 1024 * 1024) {
      throw const FormatException('DataStore 备份过大。');
    }
    final reader = _ProtoReader(bytes);
    final result = <String, String>{};
    final keys = <String>{};
    while (!reader.done) {
      final tag = reader.tag();
      if (tag != 10) {
        reader.skip(tag);
        continue;
      }
      final entry = _ProtoReader(reader.message());
      String? key;
      String? value;
      var hasValue = false;
      while (!entry.done) {
        final field = entry.tag();
        if (field == 10) {
          if (key != null) throw const FormatException('重复 DataStore 配置名。');
          key = utf8.decode(entry.message());
        } else if (field == 18) {
          if (hasValue) throw const FormatException('重复 DataStore 配置值。');
          hasValue = true;
          value = _stringValue(entry.message());
        } else {
          entry.skip(field);
        }
      }
      if (key == null || key.isEmpty || key.length > 256 || !hasValue) {
        throw const FormatException('DataStore 配置结构无效。');
      }
      if (!keys.add(key) || keys.length > 4096) {
        throw const FormatException('DataStore 配置重复或数量过多。');
      }
      if (value != null) result[key] = value;
    }
    return result;
  }

  static String? _stringValue(Uint8List bytes) {
    final reader = _ProtoReader(bytes);
    String? result;
    var populated = false;
    while (!reader.done) {
      final tag = reader.tag();
      final field = tag >> 3;
      if (field >= 1 && field <= 8) {
        const wires = [0, 5, 0, 0, 2, 2, 1, 2];
        if ((tag & 7) != wires[field - 1]) {
          throw const FormatException('DataStore 值类型无效。');
        }
        if (populated) throw const FormatException('DataStore 值存在歧义。');
        populated = true;
      }
      if (tag == 42) {
        result = utf8.decode(reader.message());
      } else {
        reader.skip(tag);
      }
    }
    if (!populated) throw const FormatException('DataStore 配置缺少值。');
    return result;
  }
}

class _ProtoReader {
  _ProtoReader(this.bytes);
  final Uint8List bytes;
  int position = 0;
  bool get done => position == bytes.length;

  int varint() {
    var value = 0;
    for (var index = 0; index < 10; index++) {
      if (done) throw const FormatException('DataStore 备份已截断。');
      final byte = bytes[position++];
      if (index == 9 && byte > 1) {
        throw const FormatException('DataStore 整数越界。');
      }
      value |= (byte & 127) << (index * 7);
      if (byte < 128) return value;
    }
    throw const FormatException('DataStore 整数无效。');
  }

  int tag() {
    final value = varint();
    if (value <= 0 || value >> 3 == 0 || value >> 3 > 0x1fffffff) {
      throw const FormatException('DataStore 字段无效。');
    }
    return value;
  }

  Uint8List message() {
    final length = varint();
    if (length < 0 || length > bytes.length - position) {
      throw const FormatException('DataStore 备份已截断。');
    }
    final result = Uint8List.sublistView(bytes, position, position + length);
    position += length;
    return result;
  }

  void skip(int tag) {
    final wire = tag & 7;
    if (wire == 0) {
      varint();
    } else if (wire == 2) {
      message();
    } else if (wire == 1 || wire == 5) {
      final length = wire == 1 ? 8 : 4;
      if (length > bytes.length - position) {
        throw const FormatException('DataStore 备份已截断。');
      }
      position += length;
    } else {
      throw const FormatException('DataStore 编码无效。');
    }
  }
}

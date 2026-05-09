use std::{collections::HashMap, fmt::Display, hash::Hash, str::Utf8Error, time::Duration};

use variadics_please::all_tuples_enumerated;

pub enum PacketError<'a> {
    UnexpectedEndOfPacket {
        type_name: &'static str,
        expected_length: usize,
        actual_length: usize,
    },
    BadUtf(Utf8Error),
    UnknownEnum {
        type_name: &'static str,
        tag: u8,
    },
    ExtraData(&'a [u8]),
}

impl<'a> Display for PacketError<'a> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PacketError::UnexpectedEndOfPacket {
                type_name,
                expected_length,
                actual_length,
            } => write!(
                f,
                "unexpected end-of-packet while parsing type {} (expected {} bytes for type, but only {} remaining)",
                type_name, expected_length, actual_length
            ),
            PacketError::BadUtf(err) => write!(f, "failed to decode utf8: {}", err),
            PacketError::UnknownEnum { type_name, tag } => {
                write!(f, "unknown tag value {} for enum {}", tag, type_name)
            }
            PacketError::ExtraData(items) => {
                write!(f, "extra {} bytes found after end-of-packet", items.len())
            }
        }
    }
}

impl<'a> From<Utf8Error> for PacketError<'a> {
    fn from(value: Utf8Error) -> Self {
        PacketError::BadUtf(value)
    }
}

pub trait BinarySerde: Sized {
    fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, PacketError<'a>>;

    fn read_packet<'a>(mut buf: &'a [u8]) -> Result<Self, PacketError<'a>> {
        let res = Self::read(&mut buf)?;
        if !buf.is_empty() {
            return Err(PacketError::ExtraData(buf));
        }

        Ok(res)
    }

    fn write(&self, buf: &mut Vec<u8>);
}

impl BinarySerde for bool {
    fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, PacketError<'a>> {
        let val: u8 = BinarySerde::read(buf)?;
        Ok(val != 0)
    }

    fn write(&self, buf: &mut Vec<u8>) {
        let val: u8 = if *self { 1 } else { 0 };
        BinarySerde::write(&val, buf);
    }
}

macro_rules! impl_for_int {
    ($ty:ty, $name:expr) => {
        impl BinarySerde for $ty {
            fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, PacketError<'a>> {
                let len = size_of::<$ty>();
                match buf.split_at_checked(len) {
                    Some((data, rest)) => {
                        *buf = rest;
                        Ok(<$ty>::from_be_bytes(data.try_into().unwrap()))
                    }
                    None => Err(PacketError::UnexpectedEndOfPacket {
                        type_name: $name,
                        expected_length: len,
                        actual_length: buf.len(),
                    }),
                }
            }

            fn write(&self, buf: &mut Vec<u8>) {
                buf.extend_from_slice(&self.to_be_bytes());
            }
        }
    };
}

impl_for_int!(i8, "i8");
impl_for_int!(i16, "i16");
impl_for_int!(i32, "i32");
impl_for_int!(i64, "i64");
impl_for_int!(i128, "i128");
impl_for_int!(isize, "isize");
impl_for_int!(u8, "u8");
impl_for_int!(u16, "u16");
impl_for_int!(u32, "u32");
impl_for_int!(u64, "u64");
impl_for_int!(u128, "u128");
impl_for_int!(usize, "usize");

impl BinarySerde for Box<[u8]> {
    fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, PacketError<'a>> {
        let len: usize = BinarySerde::read(buf)?;

        match buf.split_at_checked(len) {
            Some((data, rest)) => {
                *buf = rest;
                Ok(data.into())
            }
            None => Err(PacketError::UnexpectedEndOfPacket {
                type_name: "Box<[u8]>",
                expected_length: len,
                actual_length: buf.len(),
            }),
        }
    }

    fn write(&self, buf: &mut Vec<u8>) {
        BinarySerde::write(&self.len(), buf);
        buf.extend_from_slice(self);
    }
}

impl BinarySerde for String {
    fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, PacketError<'a>> {
        let len: usize = BinarySerde::read(buf)?;
        match buf.split_at_checked(len) {
            Some((data, rest)) => {
                *buf = rest;
                Ok(str::from_utf8(data)?.to_owned())
            }
            None => Err(PacketError::UnexpectedEndOfPacket {
                type_name: "str",
                expected_length: len,
                actual_length: buf.len(),
            }),
        }
    }

    fn write(&self, buf: &mut Vec<u8>) {
        BinarySerde::write(&self.len(), buf);
        buf.extend_from_slice(self.as_bytes());
    }
}

impl<K: Hash + Eq + BinarySerde, V: BinarySerde> BinarySerde for HashMap<K, V> {
    fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, PacketError<'a>> {
        let len: usize = BinarySerde::read(buf)?;
        let mut map: HashMap<K, V> = HashMap::new();
        for _ in 0..len {
            map.insert(BinarySerde::read(buf)?, BinarySerde::read(buf)?);
        }

        Ok(map)
    }

    fn write(&self, buf: &mut Vec<u8>) {
        BinarySerde::write(&self.len(), buf);

        for (k, v) in self {
            BinarySerde::write(k, buf);
            BinarySerde::write(v, buf);
        }
    }
}

impl<T: BinarySerde> BinarySerde for Option<T> {
    fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, PacketError<'a>> {
        let tag: u8 = BinarySerde::read(buf)?;

        match tag {
            0 => Ok(None),
            1 => Ok(Some(BinarySerde::read(buf)?)),
            _ => Err(PacketError::UnknownEnum {
                type_name: "Option",
                tag,
            }),
        }
    }

    fn write(&self, buf: &mut Vec<u8>) {
        match self {
            None => BinarySerde::write(&0u8, buf),
            Some(x) => {
                BinarySerde::write(&1u8, buf);
                BinarySerde::write(x, buf);
            }
        }
    }
}

macro_rules! impl_for_tuple {
    ($(($n:tt, $T:ident)),*) => {
        impl<$($T: BinarySerde),*> BinarySerde for ($($T,)*) {
			fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, PacketError<'a>> {
				Ok(($($T::read(buf)?,)*))
			}

			fn write(&self, buf: &mut Vec<u8>) {
				$(BinarySerde::write(&self.$n, buf));*
			}
		}
    };
}

all_tuples_enumerated!(impl_for_tuple, 1, 15, T);

impl BinarySerde for Duration {
    fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, PacketError<'a>> {
        let val: u64 = BinarySerde::read(buf)?;
        Ok(Duration::from_millis(val))
    }

    fn write(&self, buf: &mut Vec<u8>) {
        let val: u64 = self.as_millis() as u64;
        BinarySerde::write(&val, buf);
    }
}

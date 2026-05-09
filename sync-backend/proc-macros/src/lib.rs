use proc_macro2::{Span, TokenStream};
use quote::quote;
use syn::{DataEnum, DataStruct, DeriveInput, Fields, Ident, Index, parse_macro_input};

fn handle_fields(init: TokenStream, fields: &Fields) -> (TokenStream, TokenStream) {
    match &fields {
        Fields::Named(fields) => {
            let read_entries = fields.named.iter().map(|f| {
                let name = f.ident.as_ref().unwrap();
                quote! { #name: crate::protocol::BinarySerde::read(buf)? }
            });

            let write_entries = fields.named.iter().map(|f| {
                let name = Ident::new(
                    &format!("_f_{}", f.ident.as_ref().unwrap().to_string()),
                    Span::mixed_site(),
                );

                quote! { crate::protocol::BinarySerde::write(#name, buf) }
            });

            (
                quote! {
                    Ok(#init {
                        #(#read_entries,)*
                    })
                },
                quote! {
                    #(#write_entries;)*
                },
            )
        }
        Fields::Unnamed(fields) => {
            let read_entries = fields.unnamed.iter().map(|_| {
                quote! { crate::protocol::BinarySerde::read(buf)? }
            });

            let write_entries = fields.unnamed.iter().enumerate().map(|(index, _)| {
                let name = Ident::new(&format!("_f_{}", index), Span::mixed_site());

                quote! { crate::protocol::BinarySerde::write(#name, buf) }
            });

            (
                quote! {
                    Ok(#init (#(#read_entries,)*))
                },
                quote! {
                    #(#write_entries;)*
                },
            )
        }
        Fields::Unit => (quote! { Ok(#init) }, quote! {}),
    }
}

fn handle_struct(ident: Ident, data: &DataStruct) -> TokenStream {
    let (read, write) = handle_fields(quote! { #ident }, &data.fields);

    match &data.fields {
        Fields::Named(fields) => {
            let write_entries = fields.named.iter().map(|f| {
                let name = Ident::new(
                    &format!("_f_{}", f.ident.as_ref().unwrap().to_string()),
                    Span::mixed_site(),
                );

                let f_name = f.ident.as_ref().unwrap();
                quote! { let #name = &self.#f_name }
            });

            quote! {
                impl crate::protocol::BinarySerde for #ident {
                    fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, crate::protocol::PacketError<'a>> {
                        #read
                    }

                    fn write(&self, buf: &mut Vec<u8>) {
                        #(#write_entries;)*
                        #write
                    }
                }
            }
        }
        Fields::Unnamed(fields) => {
            let write_entries = fields.unnamed.iter().enumerate().map(|(index, _)| {
                let name = Ident::new(&format!("_f_{}", index), Span::mixed_site());
                let f_name = Index::from(index);
                quote! { let #name = &self.#f_name }
            });

            quote! {
                impl crate::protocol::BinarySerde for #ident {
                    fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, crate::protocol::PacketError<'a>> {
                        #read
                    }

                    fn write(&self, buf: &mut Vec<u8>) {
                        #(#write_entries;)*
                        #write
                    }
                }
            }
        }
        Fields::Unit => {
            quote! {
                impl crate::protocol::BinarySerde for #ident {
                    fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, crate::protocol::PacketError<'a>> {
                        Ok(Self)
                    }

                    fn write(&self, buf: &mut Vec<u8>) {
                    }
                }
            }
        }
    }
}

fn handle_enum(ident: Ident, data: &DataEnum) -> TokenStream {
    assert!(data.variants.len() <= 256);

    let entries: Vec<_> = data
        .variants
        .iter()
        .enumerate()
        .map(|(index, variant)| {
            let index = index as u8;

            let variant_name = &variant.ident;
            let (read, write) = handle_fields(quote! { Self::#variant_name }, &variant.fields);

            (
                quote! { #index => #read },
                match &variant.fields {
                    Fields::Named(fields) => {
                        let entries = fields.named.iter().map(|field| {
                            let name = field.ident.as_ref().unwrap();
                            let val =
                                Ident::new(&format!("_f_{}", name.to_string()), Span::mixed_site());
                            quote! { #name: #val }
                        });

                        quote! {
                            Self::#variant_name { #(#entries,)* } => {
                                crate::protocol::BinarySerde::write(&#index, buf);
                                #write
                            }
                        }
                    }
                    Fields::Unnamed(fields) => {
                        let entries = fields.unnamed.iter().enumerate().map(|(index, _)| {
                            let val = Ident::new(&format!("_f_{}", index), Span::mixed_site());
                            quote! { #val }
                        });

                        quote! {
                            Self::#variant_name(#(#entries,)*) => {
                                crate::protocol::BinarySerde::write(&#index, buf);
                                #write
                            }
                        }
                    }
                    Fields::Unit => quote! {
                        Self::#variant_name => {
                            crate::protocol::BinarySerde::write(&#index, buf);
                        }
                    },
                },
            )
        })
        .collect();

    let read = entries.iter().map(|(data, _)| data);
    let write = entries.iter().map(|(_, data)| data);

    let name = ident.to_string();

    quote! {
        impl crate::protocol::BinarySerde for #ident {
            fn read<'a>(buf: &mut &'a [u8]) -> Result<Self, crate::protocol::PacketError<'a>> {
                let desc: u8 = crate::protocol::BinarySerde::read(buf)?;
                match desc {
                    #(#read,)*
                    _ => Err(crate::protocol::PacketError::UnknownEnum {
                        type_name: #name,
                        tag: desc
                    })
                }
            }

            fn write(&self, buf: &mut Vec<u8>) {
                match self {
                    #(#write,)*
                }
            }
        }
    }
}

#[proc_macro_derive(BinarySerde, attributes(default_value))]
pub fn derive(input: proc_macro::TokenStream) -> proc_macro::TokenStream {
    let input = parse_macro_input!(input);
    let DeriveInput { ident, data, .. } = input;

    match data {
        syn::Data::Struct(data_struct) => handle_struct(ident, &data_struct),
        syn::Data::Enum(data_enum) => handle_enum(ident, &data_enum),
        syn::Data::Union(_) => quote! { compile_error!("untagged union not supported") },
    }
    .into()
}


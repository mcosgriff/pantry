# Changelog

## [0.2.0](https://github.com/mcosgriff/pantry/compare/v0.1.0...v0.2.0) (2026-11-12)


### Features

* contribute new products to Open Food Facts ([#65](https://github.com/mcosgriff/pantry/issues/65)) ([f6d0cd4](https://github.com/mcosgriff/pantry/commit/f6d0cd457fa2c1fd09607530f8fb18a3190e4b73))
* **domain:** amount model with units, packages, densities and conversions ([#58](https://github.com/mcosgriff/pantry/issues/58)) ([ecd9332](https://github.com/mcosgriff/pantry/commit/ecd93329c83fbf7af7ae868a3da3b0b7e0feb133))
* expiry notifications and a daily digest ([#64](https://github.com/mcosgriff/pantry/issues/64)) ([38b36f0](https://github.com/mcosgriff/pantry/commit/38b36f03602c4b2d92510df5a8e3169bf84db320)), closes [#27](https://github.com/mcosgriff/pantry/issues/27)
* import a recipe from a link into Meal prep ([#66](https://github.com/mcosgriff/pantry/issues/66)) ([2c27e7d](https://github.com/mcosgriff/pantry/commit/2c27e7d3c4d96c261f8a826c64ebdc5692dcd918)), closes [#43](https://github.com/mcosgriff/pantry/issues/43)
* meal prep compares and subtracts amounts across units ([#68](https://github.com/mcosgriff/pantry/issues/68)) ([812170f](https://github.com/mcosgriff/pantry/commit/812170fcc807402f950cc590a7e62ce3dbf0cde1))
* removing an item asks what happened ([#60](https://github.com/mcosgriff/pantry/issues/60)) ([909fe78](https://github.com/mcosgriff/pantry/commit/909fe786792328c4c5fff75396a5767439785f68)), closes [#18](https://github.com/mcosgriff/pantry/issues/18)
* store items and ingredients with one amount shape (schema 5) ([#63](https://github.com/mcosgriff/pantry/issues/63)) ([66e2ef0](https://github.com/mcosgriff/pantry/commit/66e2ef004b5026ea88865872640e713a18c245df)), closes [#56](https://github.com/mcosgriff/pantry/issues/56)
* unit pickers, package contents and a measurement setting ([#71](https://github.com/mcosgriff/pantry/issues/71)) ([10afda1](https://github.com/mcosgriff/pantry/commit/10afda15002aa4c5f9275f92a8f73a53cf21e6d6)), closes [#57](https://github.com/mcosgriff/pantry/issues/57)


### Bug Fixes

* one consumption module; a hidden duplicate no longer survives a trip ([#53](https://github.com/mcosgriff/pantry/issues/53)) ([6096a19](https://github.com/mcosgriff/pantry/commit/6096a190fc9d942afc5ff2518aeb89e6ecaa5823))
* recipe import builds against schema 5 and keeps every amount ([#67](https://github.com/mcosgriff/pantry/issues/67)) ([539be25](https://github.com/mcosgriff/pantry/commit/539be2562289ddcf8ee9c3454aa9fca246ccab35))


### Code Refactoring

* one item intake module from barcode to saved item; deep modules take the Clock ([#69](https://github.com/mcosgriff/pantry/issues/69)) ([4eade74](https://github.com/mcosgriff/pantry/commit/4eade7430f9ed46787d82ced2b5b29c8ff22d338))
* one locations module for identity, resolution and the deleted-location fallback ([#62](https://github.com/mcosgriff/pantry/issues/62)) ([1a94706](https://github.com/mcosgriff/pantry/commit/1a94706bc6b4a396420f41c121fe74eaa3a9692a)), closes [#51](https://github.com/mcosgriff/pantry/issues/51)
* one meal plan editing module for servings, amounts and save-and-plan ([#70](https://github.com/mcosgriff/pantry/issues/70)) ([3a77f0b](https://github.com/mcosgriff/pantry/commit/3a77f0b60e6773daf185daf745e6c3c3391c377c)), closes [#50](https://github.com/mcosgriff/pantry/issues/50)

## 0.1.0 (2026-11-11)


### Features

* **data:** store the pantry in Room 3 and settings in DataStore ([#24](https://github.com/mcosgriff/pantry/issues/24)) ([94ba906](https://github.com/mcosgriff/pantry/commit/94ba9067bd98984b79db583ae7b0b2db3158af62))
* **domain:** pantry item model, quantities and expiry rules ([#22](https://github.com/mcosgriff/pantry/issues/22)) ([df37151](https://github.com/mcosgriff/pantry/commit/df3715169219c0c3bfb0e795ae630757c7fffd6a))
* item details screen to add, edit and remove items ([#26](https://github.com/mcosgriff/pantry/issues/26)) ([dd1ea14](https://github.com/mcosgriff/pantry/commit/dd1ea1406291c2284560a806f67de727d0bd196a))
* look up unknown barcodes in Open Food Facts ([#44](https://github.com/mcosgriff/pantry/issues/44)) ([0860bdd](https://github.com/mcosgriff/pantry/commit/0860bddf5efc0d25478b6512a22401346ea14398)), closes [#9](https://github.com/mcosgriff/pantry/issues/9)
* meal prep, planned from what's in the pantry ([#41](https://github.com/mcosgriff/pantry/issues/41)) ([018e71c](https://github.com/mcosgriff/pantry/commit/018e71c0d1ab76e6013ac50fe4f14927c415180c)), closes [#19](https://github.com/mcosgriff/pantry/issues/19)
* scanning, store-packed labels, haul review and shopping list ([#34](https://github.com/mcosgriff/pantry/issues/34)) ([862f300](https://github.com/mcosgriff/pantry/commit/862f3006df92d878afe521e0a0137ee44d02ca3b))
* use soon screen with quick actions ([#29](https://github.com/mcosgriff/pantry/issues/29)) ([e57eaa4](https://github.com/mcosgriff/pantry/commit/e57eaa495326ebb58b47a5e615c51a3f9270f806))

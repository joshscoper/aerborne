from test_app import SiteTest


class StoreArtTest(SiteTest):
    def test_rank_artwork_and_upgrade_preserve_product_settings(self):
        from aerborne import seed_updates
        homepage = self.client.get('/store').get_data(as_text=True)
        category = self.client.get('/store/supporter-ranks').get_data(as_text=True)
        conn = self.db()
        for rank in ('skyfarer', 'cloudkeeper', 'starweaver'):
            for size in (128, 256, 512):
                asset = '/static/img/store/ranks/%s-%d.webp' % (rank, size)
                response = self.client.get(asset)
                self.assertEqual(response.status_code, 200, asset)
                self.assertEqual(response.mimetype, 'image/webp')
            self.assertIn('ranks/%s-256.webp' % rank, homepage)
            self.assertIn('ranks/%s-128.webp' % rank, category)
            product = self.client.get('/store/item/' + rank).get_data(as_text=True)
            self.assertIn('ranks/%s-512.webp' % rank, product)
            self.assertNotIn('Concept art. The in-game look may differ.', product)
        conn.execute("UPDATE products SET image='static:img/kit/ranks/skyfarer-320.webp', concept_art=1, price='$12', includes='Custom benefits' WHERE slug='skyfarer'")
        conn.execute("UPDATE products SET image='static:img/custom-rank.webp', concept_art=1 WHERE slug='cloudkeeper'")
        before = dict(conn.execute("SELECT * FROM products WHERE slug='skyfarer'").fetchone())
        conn.execute("UPDATE settings SET value='12' WHERE key='seed_rev'")
        conn.commit()
        self.assertTrue(seed_updates.apply(conn))
        after = dict(conn.execute("SELECT * FROM products WHERE slug='skyfarer'").fetchone())
        self.assertEqual(after, dict(before, image='static:img/store/ranks/skyfarer-256.webp', concept_art=0))
        custom = conn.execute("SELECT image, concept_art FROM products WHERE slug='cloudkeeper'").fetchone()
        self.assertEqual(tuple(custom), ('static:img/custom-rank.webp', 1))
        self.assertIn('static/img/custom-rank.webp', self.client.get('/store/item/cloudkeeper').get_data(as_text=True))
        self.assertFalse(seed_updates.apply(conn))
        conn.close()

    def test_artwork_routes_and_empty_collections(self):
        names = ('category-ranks', 'category-companions', 'category-trails', 'category-bundles', 'category-island-decor',
                 'shopkeeper-mascot', 'empty-cart-illustration')
        for name in names:
            for size in (256, 512):
                path = '/static/img/store/%s-%d.webp' % (name, size)
                response = self.client.get(path)
                self.assertEqual(response.status_code, 200, path)
                self.assertEqual(response.mimetype, 'image/webp')
        homepage = self.client.get('/store').get_data(as_text=True)
        for name in names[:5]:
            self.assertIn(name + '-256.webp', homepage)
        self.assertIn('shopkeeper-mascot-256.webp', homepage)
        for path in ('/store/companions', '/store/supporter-ranks', '/store/item/skyfarer'):
            page = self.client.get(path)
            self.assertEqual(page.status_code, 200)
            self.assertIn(b'aria-label="Store collections"', page.data)
            self.assertNotIn(b'empty-cart-illustration-', page.data)
        empty = self.client.get('/store/island-decor').get_data(as_text=True)
        self.assertIn('category-island-decor-256.webp', empty)
        self.assertIn('empty-cart-illustration-256.webp', empty)
        self.assertIn('This collection doesn’t have any items yet.', empty)
        self.assertIn('shopkeeper-mascot-256.webp', empty)

    def test_sale_counts_require_a_checkout_link(self):
        conn = self.db()
        conn.execute("UPDATE products SET availability='released', price='$9.99', tebex_url='' WHERE slug='skyfarer'")
        conn.commit()
        self.assertNotIn(b'1 on sale', self.client.get('/store').data)
        page = self.client.get('/store/item/skyfarer').get_data(as_text=True)
        self.assertNotIn('Buy on Tebex', page)
        self.assertNotIn('$9.99', page)
        conn.execute("UPDATE products SET tebex_url='https://example.tebex.io/package/1' WHERE slug='skyfarer'")
        conn.commit()
        self.assertIn(b'1 on sale', self.client.get('/store').data)
        self.assertIn(b'Buy on Tebex for $9.99', self.client.get('/store/item/skyfarer').data)
        conn.close()


class StoreSuiteTest(SiteTest):
    def test_new_collections_are_seeded_with_their_game_products_and_art(self):
        from aerborne import seed_updates
        conn = self.db()
        for product in seed_updates.REV31_PRODUCTS:
            row = conn.execute("SELECT * FROM products WHERE slug = ?", (product['slug'],)).fetchone()
            self.assertIsNotNone(row, product['slug'])
            self.assertEqual(row['game_product'], product['game_product'])
            asset = '/static/' + product['image'][len('static:'):]
            self.assertEqual(self.client.get(asset).status_code, 200, asset)
        booster = conn.execute("SELECT * FROM products WHERE slug = 'xp-booster'").fetchone()
        self.assertEqual((booster['category'], booster['entitlement']), ('xp-boosters', 'one-time'))
        conn.close()
        front = self.client.get('/store').get_data(as_text=True)
        for title in ('XP boosters', 'Titles', 'Auras', 'Finishers'):
            self.assertIn(title, front)
        self.assertIn('Find it in game', front)
        for category in ('titles', 'auras', 'finishers', 'xp-boosters'):
            self.assertEqual(self.client.get('/store/' + category).status_code, 200, category)
        self.assertIn('Bonus XP for everyone online', self.client.get('/store/item/xp-booster').get_data(as_text=True))

    def test_rank_comparison_reads_at_a_glance(self):
        page = self.client.get('/store/supporter-ranks').get_data(as_text=True)
        self.assertIn('Best value', page)
        self.assertIn('rank-tick', page)
        self.assertIn('Not included', page, 'screen readers still hear what a dash means')
        self.assertIn('Shown in chat, the tab list and above your head', page)

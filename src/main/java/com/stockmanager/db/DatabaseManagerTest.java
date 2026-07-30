package com.stockmanager.db;

import static org.junit.jupiter.api.Assertions.*;

import com.stockmanager.model.Product;
import com.stockmanager.model.Sale;
import com.stockmanager.model.SaleItem;
import com.stockmanager.util.AutoUpdater;
import com.stockmanager.util.PasswordUtil;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.Base64;

public class DatabaseManagerTest {

    @Test
    public void testPasswordHashing() {
        String password = "mySecretPassword123";
        String hash = PasswordUtil.hash(password);
        assertNotNull(hash, "Password hash should not be null.");
        assertTrue(PasswordUtil.verify(password, hash), "Password verification should succeed.");
        assertFalse(PasswordUtil.verify("wrongPassword", hash), "Password verification should fail for incorrect password.");
    }

    @Test
    public void testAutoUpdaterSignatureVerification() throws Exception {
        // Generate a temporary keypair for the test
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        String pubBase64 = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());

        // Create temporary file with test data
        Path tempFile = Files.createTempFile("test-sig-verify-", ".txt");
        byte[] content = "Hello World Update Content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(tempFile, content);

        // Sign the content
        Signature sign = Signature.getInstance("SHA256withRSA");
        sign.initSign(kp.getPrivate());
        sign.update(content);
        byte[] sigBytes = sign.sign();
        String sigBase64 = Base64.getEncoder().encodeToString(sigBytes);

        // Verify via AutoUpdater helper
        boolean isValid = AutoUpdater.verifyFileSignature(tempFile, sigBase64, pubBase64);
        assertTrue(isValid, "Cryptographic signature verification should succeed.");

        // Verify with invalid signature
        boolean isInvalid = AutoUpdater.verifyFileSignature(tempFile, "invalid-sig", pubBase64);
        assertFalse(isInvalid, "Cryptographic signature verification should fail for invalid signature.");

        Files.deleteIfExists(tempFile);
    }

    @Test
    public void testBigDecimalMoneyModels() {
        Product p = new Product();
        p.setCostPrice(new BigDecimal("150.50"));
        p.setSellingPrice(new BigDecimal("299.99"));

        assertEquals(new BigDecimal("150.50"), p.getCostPrice(), "Cost price should match.");
        assertEquals(new BigDecimal("299.99"), p.getSellingPrice(), "Selling price should match.");

        SaleItem item = new SaleItem(1, 10, "Test Product", 3, new BigDecimal("299.99"));
        assertEquals(new BigDecimal("899.97"), item.getSubtotal(), "Subtotal should calculate exactly.");

        Sale sale = new Sale();
        sale.setTotalAmount(new BigDecimal("899.97"));
        assertEquals(new BigDecimal("899.97"), sale.getTotalAmount(), "Total amount should match.");
    }
}

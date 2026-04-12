package com.superredrock.usbthief.gui;

import com.superredrock.usbthief.core.Version;

import javax.swing.*;

public class BlankFrame extends JFrame {
    public BlankFrame(){
        setTitle( " v" + Version.getVersion());
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
    }
}

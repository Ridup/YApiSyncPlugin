package com.ridup.swing.icon;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * @author mu_ran@yahoo.com
 * @version V3.0
 * @since 3.0.1 2020/6/22 2:15
 */
public class Uploading implements Icon {

    private static final JFrame jframe = new JFrame();//创建一个窗体

    private static final Box base = Box.createVerticalBox();//设置盒子，用来存放 jlabel 标签
    private static final Box box1 = Box.createHorizontalBox();
    private static final Box box2 = Box.createHorizontalBox();

    private int height;//图标的高
    private int width;//图标的宽

    public Uploading(int weight, int height) {    //定义 Uploading  的构造方法

        this.width = weight;
        this.height = height;
    }

    public static void main(String[] args) {
        // TODO Auto-generated method stub

        //方法一：自己绘制图形
        Uploading icon = new Uploading(20, 20);

        //方法二：自己添加图标
        ImageIcon image = new ImageIcon("./static/images/uploading.png");//设置图片的来源路径（图片的URL）
        image.setImage(image.getImage()
            .getScaledInstance(100, 100, 100));//设置图片大小

        jframe.setTitle("实现Icon组件窗体");//设置窗体标题
        jframe.setBounds(600, 600, 600, 600);//设置窗体大小
        jframe.setBackground(Color.WHITE);//设置窗体背景颜色
        jframe.setVisible(true);//设置窗体可见性

        JLabel jlabel = new JLabel("标签内容", icon, SwingConstants.CENTER);//创建一个标签用于存放图标icon
        JLabel jlabel2 = new JLabel();

        //jlabel.setHorizontalAlignment(SwingConstants.CENTER);//设置标签内容水平对齐
        //jlabel.setVerticalAlignment(SwingConstants.CENTER);//设置标签内容垂直对齐

        jlabel2.setIcon(image);//将图片添加到标签中

        box1.add(jlabel);
        box2.add(jlabel2);
        base.add(box1);
        base.add(box2);

        jframe.add(base);

        jframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);//设置窗体默认关闭方式

    }

    @Override
    public int getIconHeight() {
        // TODO Auto-generated method stub
        return this.height;
    }

    @Override
    public int getIconWidth() {
        // TODO Auto-generated method stub
        return this.width;
    }

    @Override
    public void paintIcon(Component arg0, Graphics arg1, int x, int y) {
        // TODO Auto-generated method stub
        arg1.fillOval(x, y, width, height);//绘制一个圆形
        arg1.setColor(Color.GREEN);///设置圆形的颜色为绿色

    }

}

/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
Part of the Processing project - http://processing.org
Copyright (c) 2012-15 The Processing Foundation

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License version 2
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation, Inc.
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
*/

package processing.mode.java.pdex;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.Painter;
import javax.swing.SwingWorker;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.BadLocationException;

import processing.app.Base;
import processing.app.Messages;
import processing.app.Mode;
import processing.app.syntax.JEditTextArea;
import processing.mode.java.JavaEditor;


public class CompletionPanel {
  /**
   * The completion list generated by ASTGenerator
   */
  private JList<CompletionCandidate> completionList;

  /**
   * The popup menu in which the suggestion list is shown
   */
  private JPopupMenu popupMenu;

  /**
   * Partial word which triggered the code completion and which needs to be completed
   */
  private String subWord;

  /**
   * Postion where the completion has to be inserted
   */
  private int insertionPosition;

  private JavaTextArea textarea;

  /**
   * Scroll pane in which the completion list is displayed
   */
  private JScrollPane scrollPane;

  protected JavaEditor editor;

  static protected final int MOUSE_COMPLETION = 10, KEYBOARD_COMPLETION = 20;

  private boolean horizontalScrollBarVisible = false;

  static public ImageIcon classIcon;
  static public ImageIcon fieldIcon;
  static public ImageIcon methodIcon;
  static public ImageIcon localVarIcon;


  /**
   * Triggers the completion popup
   * @param textarea
   * @param position - insertion position(caret pos)
   * @param subWord - Partial word which triggered the code completion and which needs to be completed
   * @param items - completion candidates
   * @param location - Point location where popup list is to be displayed
   * @param dedit
   */
  public CompletionPanel(final JEditTextArea textarea,
                         int position, String subWord,
                         DefaultListModel<CompletionCandidate> items,
                         final Point location, JavaEditor editor) {
    this.textarea = (JavaTextArea) textarea;
    this.editor = editor;
    this.insertionPosition = position;
    if (subWord.indexOf('.') != -1 && subWord.indexOf('.') != subWord.length()-1) {
      this.subWord = subWord.substring(subWord.lastIndexOf('.') + 1);
    } else {
      this.subWord = subWord;
    }

    loadIcons();
    popupMenu = new JPopupMenu();
    popupMenu.removeAll();
    popupMenu.setOpaque(false);
    popupMenu.setBorder(null);

    scrollPane = new JScrollPane();
    styleScrollPane();
    scrollPane.setViewportView(completionList = createSuggestionList(position, items));
    popupMenu.add(scrollPane, BorderLayout.CENTER);
    popupMenu.setPopupSize(calcWidth(), calcHeight(items.getSize())); //TODO: Eradicate this evil
    popupMenu.setFocusable(false);
    ASTGenerator astGenerator = editor.getErrorChecker().getASTGenerator();
    synchronized (astGenerator) {
      astGenerator.updateJavaDoc(completionList.getSelectedValue());
    }
    popupMenu.show(textarea, location.x, textarea.getBaseline(0, 0) + location.y);
    //log("Suggestion shown: " + System.currentTimeMillis());
  }


  private void loadIcons() {
    if (classIcon == null) {
      Mode mode = editor.getMode();
      classIcon = mode.loadIcon("theme/completion/class_obj.png");
      methodIcon = mode.loadIcon("theme/completion/methpub_obj.png");
      fieldIcon = mode.loadIcon("theme/completion/field_protected_obj.png");
      localVarIcon = mode.loadIcon("theme/completion/field_default_obj.png");
    }
  }


  private void styleScrollPane() {
    String laf = UIManager.getLookAndFeel().getID();
    if (!laf.equals("Nimbus") && !laf.equals("Windows")) return;

    String thumbColor = null;
    if (laf.equals("Nimbus")) {
      UIDefaults defaults = new UIDefaults();
      defaults.put("PopupMenu.contentMargins", new InsetsUIResource(0, 0, 0, 0));
      defaults.put("ScrollPane[Enabled].borderPainter", new Painter<JComponent>() {
        public void paint(Graphics2D g, JComponent t, int w, int h) {}
      });
      popupMenu.putClientProperty("Nimbus.Overrides", defaults);
      scrollPane.putClientProperty("Nimbus.Overrides", defaults);
      thumbColor = "nimbusBlueGrey";
    } else if (laf.equals("Windows")) {
      thumbColor = "ScrollBar.thumbShadow";
    }

    scrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(Integer.MAX_VALUE, 8));
    scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(8, Integer.MAX_VALUE));
    scrollPane.getHorizontalScrollBar().setUI(new CompletionScrollBarUI(thumbColor));
    scrollPane.getVerticalScrollBar().setUI(new CompletionScrollBarUI(thumbColor));
  }


  private static class CompletionScrollBarUI extends BasicScrollBarUI {
    private String thumbColorName;

    protected CompletionScrollBarUI(String thumbColorName) {
      this.thumbColorName = thumbColorName;
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle trackBounds) {
      g.setColor((Color) UIManager.get(thumbColorName));
      g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
      return createZeroButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
      return createZeroButton();
    }

    static private JButton createZeroButton() {
      JButton jbutton = new JButton();
      jbutton.setPreferredSize(new Dimension(0, 0));
      jbutton.setMinimumSize(new Dimension(0, 0));
      jbutton.setMaximumSize(new Dimension(0, 0));
      return jbutton;
    }
  }


  public boolean isVisible() {
    return popupMenu.isVisible();
  }


  public void setInvisible() {
    popupMenu.setVisible(false);
  }


  /**
   * Dynamic height of completion panel depending on item count
   */
  private int calcHeight(int itemCount) {
    int maxHeight = 250;
    FontMetrics fm = textarea.getGraphics().getFontMetrics();
    float itemHeight = Math.max((fm.getHeight() + (fm.getDescent()) * 0.5f),
                                classIcon.getIconHeight() * 1.2f);

    if (horizontalScrollBarVisible) {
      itemCount++;
    }

    if (itemCount < 4) {
      itemHeight *= 1.3f; //Sorry, but it works.
    }

    float h = itemHeight * (itemCount);

    if (itemCount >= 4) {
    	h += itemHeight * 0.3; // a bit of offset
    }

    return Math.min(maxHeight, (int) h); // popup menu height
  }


  /**
   * Dynamic width of completion panel
   * @return - width
   */
  private int calcWidth() {
    int maxWidth = 300;
    float min = 0;
    FontMetrics fm = textarea.getGraphics().getFontMetrics();
    for (int i = 0; i < completionList.getModel().getSize(); i++) {
      float h = fm.stringWidth(completionList.getModel().getElementAt(i).getLabel());
      min = Math.max(min, h);
    }
    int w = Math.min((int) min, maxWidth);
    horizontalScrollBarVisible = (w == maxWidth);
    w += classIcon.getIconWidth(); // add icon width too!
    w += fm.stringWidth("           "); // a bit of offset
    //log("popup width " + w);
    return w; // popup menu width
  }


  /**
   * Created the popup list to be displayed
   * @param position
   * @param items
   * @return
   */
  private JList<CompletionCandidate> createSuggestionList(final int position,
                                    final DefaultListModel<CompletionCandidate> items) {

    JList<CompletionCandidate> list = new JList<CompletionCandidate>(items);
    //list.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setSelectedIndex(0);
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          insertSelection(MOUSE_COMPLETION);
          setInvisible();
        }
      }
    });
    list.setCellRenderer(new CustomListRenderer());
    list.setFocusable(false);
    return list;
  }


  /*
  // possibly defunct
  private boolean updateList(final DefaultListModel<CompletionCandidate> items, String newSubword,
                            final Point location, int position) {
    this.subWord = new String(newSubword);
    if (subWord.indexOf('.') != -1)
      this.subWord = subWord.substring(subWord.lastIndexOf('.') + 1);
    insertionPosition = position;
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        scrollPane.getViewport().removeAll();
        completionList.setModel(items);
        completionList.setSelectedIndex(0);
        scrollPane.setViewportView(completionList);
        popupMenu.setPopupSize(calcWidth(), calcHeight(items.getSize()));
        //log("Suggestion updated" + System.nanoTime());
        textarea.requestFocusInWindow();
        popupMenu.show(textarea, location.x, textarea.getBaseline(0, 0)
            + location.y);
        completionList.validate();
        scrollPane.validate();
        popupMenu.validate();
      }
    });
    return true;
  }
  */


  /**
   * Inserts the CompletionCandidate chosen from the suggestion list
   * @param completionSource - whether being completed via keypress or mouse click.
   * @return true - if code was successfully inserted at the caret position
   */
  protected boolean insertSelection(int completionSource) {
    if (completionList.getSelectedValue() != null) {
      try {
        // If user types 'abc.', subword becomes '.' and null is returned
        String currentSubword = fetchCurrentSubword();
        int currentSubwordLen = currentSubword == null ? 0 : currentSubword
            .length();
        //logE(currentSubword + " <= subword,len => " + currentSubword.length());
        String selectedSuggestion =
          completionList.getSelectedValue().getCompletionString();

        if (currentSubword != null) {
          selectedSuggestion = selectedSuggestion.substring(currentSubwordLen);
        } else {
          currentSubword = "";
        }

        String completionString =
            completionList.getSelectedValue().getCompletionString();
        if (selectedSuggestion.endsWith(" )")) { // the case of single param methods
          // selectedSuggestion = ")";
          if (completionString.endsWith(" )")) {
            completionString = completionString.substring(0, completionString
                .length() - 2)
                + ")";
          }
        }

        boolean mouseClickOnOverloadedMethods = false;
        if (completionSource == MOUSE_COMPLETION) {
          // The case of overloaded methods, displayed as 'foo(...)'
          // They have completion strings as 'foo('. See #2755
          if (completionString.endsWith("(")) {
            mouseClickOnOverloadedMethods = true;
          }
        }

        Messages.loge(subWord + " <= subword, Inserting suggestion=> "
            + selectedSuggestion + " Current sub: " + currentSubword);
        if (currentSubword.length() > 0) {
          textarea.getDocument().remove(insertionPosition - currentSubwordLen,
                                        currentSubwordLen);
        }

        textarea.getDocument()
            .insertString(insertionPosition - currentSubwordLen,
                          completionString, null);
        if (selectedSuggestion.endsWith(")") && !selectedSuggestion.endsWith("()")) {
          // place the caret between '( and first ','
          int x = selectedSuggestion.indexOf(',');
          if(x == -1) {
            // the case of single param methods, containing no ','
            textarea.setCaretPosition(textarea.getCaretPosition() - 1); // just before ')'
          } else {
            textarea.setCaretPosition(insertionPosition + x);
          }
        }

        Messages.log("Suggestion inserted: " + System.currentTimeMillis());
        if (completionList.getSelectedValue().getLabel().contains("...")) {
          // log("No hide");
          // Why not hide it? Coz this is the case of
          // overloaded methods. See #2755
        } else {
          setInvisible();
        }

        if(mouseClickOnOverloadedMethods) {
          // See #2755
          ((JavaTextArea) editor.getTextArea()).fetchPhrase();
        }

        return true;
      } catch (BadLocationException e1) {
        e1.printStackTrace();
      } catch (Exception e) {
        e.printStackTrace();
      }
      setInvisible();
    }
    return false;
  }


  private String fetchCurrentSubword() {
    //log("Entering fetchCurrentSubword");
    JEditTextArea ta = editor.getTextArea();
    int off = ta.getCaretPosition();
    //log2("off " + off);
    if (off < 0)
      return null;
    int line = ta.getCaretLine();
    if (line < 0)
      return null;
    String s = ta.getLineText(line);
    //log2("lin " + line);
    //log2(s + " len " + s.length());

    int x = ta.getCaretPosition() - ta.getLineStartOffset(line) - 1, x1 = x - 1;
    if (x >= s.length() || x < 0)
      return null; //TODO: Does this check cause problems? Verify.
    if (Base.DEBUG) System.out.print(" x char: " + s.charAt(x));
    //int xLS = off - getLineStartNonWhiteSpaceOffset(line);

    String word = (x < s.length() ? s.charAt(x) : "") + "";
    if (s.trim().length() == 1) {
    //      word = ""
    //          + (keyChar == KeyEvent.CHAR_UNDEFINED ? s.charAt(x - 1) : keyChar);
          //word = (x < s.length()?s.charAt(x):"") + "";
      word = word.trim();
      if (word.endsWith("."))
        word = word.substring(0, word.length() - 1);

      return word;
    }
    //log("fetchCurrentSubword 1 " + word);
    if(word.equals(".")) return null; // If user types 'abc.', subword becomes '.'
    //    if (keyChar == KeyEvent.VK_BACK_SPACE || keyChar == KeyEvent.VK_DELETE)
    //      ; // accepted these keys
    //    else if (!(Character.isLetterOrDigit(keyChar) || keyChar == '_' || keyChar == '$'))
    //      return null;
    int i = 0;

    while (true) {
      i++;
      //TODO: currently works on single line only. "a. <new line> b()" won't be detected
      if (x1 >= 0) {
//        if (s.charAt(x1) != ';' && s.charAt(x1) != ',' && s.charAt(x1) != '(')
        if (Character.isLetterOrDigit(s.charAt(x1)) || s.charAt(x1) == '_') {

          word = s.charAt(x1--) + word;

        } else {
          break;
        }
      } else {
        break;
      }
      if (i > 200) {
        // time out!
        break;
      }
    }
    //    if (keyChar != KeyEvent.CHAR_UNDEFINED)
    //log("fetchCurrentSubword 2 " + word);
    if (Character.isDigit(word.charAt(0)))
      return null;
    word = word.trim();
    if (word.endsWith("."))
      word = word.substring(0, word.length() - 1);
    //log("fetchCurrentSubword 3 " + word);
    //showSuggestionLater();
    return word;
    //}
  }


  /**
   * When up arrow key is pressed, moves the highlighted selection up in the list
   */
  protected void moveUp() {
    if (completionList.getSelectedIndex() == 0) {
      scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getMaximum());
      selectIndex(completionList.getModel().getSize() - 1);

    } else {
      int index = Math.max(completionList.getSelectedIndex() - 1, 0);
      selectIndex(index);
      int step = scrollPane.getVerticalScrollBar().getMaximum()
          / completionList.getModel().getSize();
      scrollPane.getVerticalScrollBar().setValue(scrollPane
                                                 .getVerticalScrollBar()
                                                 .getValue()
                                                 - step);
      ASTGenerator astGenerator = editor.getErrorChecker().getASTGenerator();
      synchronized (astGenerator) {
        astGenerator.updateJavaDoc(completionList.getSelectedValue());
      }
    }
  }


  /**
   * When down arrow key is pressed, moves the highlighted selection down in the list
   */
  protected void moveDown() {
    if (completionList.getSelectedIndex() == completionList.getModel().getSize() - 1) {
      scrollPane.getVerticalScrollBar().setValue(0);
      selectIndex(0);

    } else {
      int index = Math.min(completionList.getSelectedIndex() + 1,
                           completionList.getModel().getSize() - 1);
      selectIndex(index);
      ASTGenerator astGenerator = editor.getErrorChecker().getASTGenerator();
      synchronized (astGenerator) {
        astGenerator.updateJavaDoc(completionList.getSelectedValue());
      }
      int step = scrollPane.getVerticalScrollBar().getMaximum() / completionList.getModel().getSize();
      scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getValue() + step);
    }
  }


  private void selectIndex(int index) {
    completionList.setSelectedIndex(index);
  }


  /**
   * Custom cell renderer to display icons along with the completion candidates
   * @author Manindra Moharana <me@mkmoharana.com>
   *
   */
  private static class CustomListRenderer extends javax.swing.DefaultListCellRenderer {

    public Component getListCellRendererComponent(JList<?> list, Object value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
      JLabel label = (JLabel) super.getListCellRendererComponent(list, value,
                                                                 index,
                                                                 isSelected,
                                                                 cellHasFocus);
      if (value instanceof CompletionCandidate) {
        CompletionCandidate cc = (CompletionCandidate) value;
        switch (cc.getType()) {
        case CompletionCandidate.LOCAL_VAR:
          label.setIcon(localVarIcon);
          break;
        case CompletionCandidate.LOCAL_FIELD:
        case CompletionCandidate.PREDEF_FIELD:
          label.setIcon(fieldIcon);
          break;
        case CompletionCandidate.LOCAL_METHOD:
        case CompletionCandidate.PREDEF_METHOD:
          label.setIcon(methodIcon);
          break;
        case CompletionCandidate.LOCAL_CLASS:
        case CompletionCandidate.PREDEF_CLASS:
          label.setIcon(classIcon);
          break;

        default:
          Messages.log("(CustomListRenderer)Unknown CompletionCandidate type " + cc.getType());
          break;
        }
      } else {
        Messages.log("(CustomListRenderer)Unknown CompletionCandidate object " + value);
      }
      return label;
    }
  }
}

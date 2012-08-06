package org.mskcc.mondrian.internal.gui.heatmap;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.cytoscape.application.swing.CytoPanelComponent;
import org.cytoscape.application.swing.CytoPanelName;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyTable;
import org.cytoscape.model.events.RowsSetEvent;
import org.cytoscape.model.events.RowsSetListener;
import org.cytoscape.model.events.TableAddedEvent;
import org.cytoscape.model.events.TableAddedListener;
import org.cytoscape.model.events.TableDeletedEvent;
import org.cytoscape.model.events.TableDeletedListener;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.swing.DialogTaskManager;
import org.mskcc.mondrian.client.CaseList;
import org.mskcc.mondrian.client.GeneticProfile;
import org.mskcc.mondrian.internal.MondrianApp;
import org.mskcc.mondrian.internal.configuration.ConfigurationChangedEvent;
import org.mskcc.mondrian.internal.configuration.ConfigurationChangedEvent.Type;
import org.mskcc.mondrian.internal.configuration.MondrianConfiguration;
import org.mskcc.mondrian.internal.configuration.MondrianConfigurationListener;
import org.mskcc.mondrian.internal.configuration.MondrianCyTable;
import org.mskcc.mondrian.internal.gui.heatmap.ColorGradientWidget.LEGEND_POSITION;
import org.mskcc.mondrian.internal.gui.heatmap.HeatmapPanelConfiguration.PROPERTY_TYPE;
import java.awt.BorderLayout;

@SuppressWarnings("serial")
public class HeatmapPanel extends JPanel implements MondrianConfigurationListener, 
CytoPanelComponent, ActionListener {
	
	private JComboBox constantPropertyTypeComboBox;
	private JComboBox constantPropertyComboBox;
	private JCheckBox hideGenesCheckBox;
	private HeatmapPanelConfiguration configuration;
	private JScrollPane scrollPane;
	private ColorGradientWidget legend;	
	
	/**
	 * Create the panel.
	 */
	public HeatmapPanel() {
		setLayout(new BorderLayout(0, 0));
		
		JPanel headerPane = new JPanel();
		add(headerPane, BorderLayout.NORTH);
		headerPane.setLayout(new BoxLayout(headerPane, BoxLayout.X_AXIS));
		
		constantPropertyTypeComboBox = new JComboBox(){
            /** 
             * @inherited <p>
             */
            @Override
            public Dimension getMaximumSize() {
                Dimension max = super.getMaximumSize();
                max.height = getPreferredSize().height;
                return max;
            }

        };
		constantPropertyTypeComboBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				DialogTaskManager taskManager = MondrianApp.getInstance().getTaskManager();
				taskManager.execute(new TaskIterator(new UpdateConstantTypeTask()));					
			}
		});;
		constantPropertyTypeComboBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		constantPropertyTypeComboBox.setModel(new DefaultComboBoxModel(org.mskcc.mondrian.internal.gui.heatmap.HeatmapPanelConfiguration.PROPERTY_TYPE.values()));
		headerPane.add(constantPropertyTypeComboBox);
		
		constantPropertyComboBox = new JComboBox(){
            /** 
             * @inherited <p>
             */
            @Override
            public Dimension getMaximumSize() {
                Dimension max = super.getMaximumSize();
                max.height = getPreferredSize().height;
                return max;
            }

        };
        constantPropertyComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				DialogTaskManager taskManager = MondrianApp.getInstance().getTaskManager();
				taskManager.execute(new TaskIterator(new UpdateHeatmapTableTask()));	
			}
		});
		headerPane.add(constantPropertyComboBox);
		
		headerPane.add(Box.createHorizontalGlue());
		
		hideGenesCheckBox = new JCheckBox("Hide Genes without Data");
		headerPane.add(hideGenesCheckBox);
		
		JButton configGradientButton = new JButton("Configure Color Gradient");
		headerPane.add(configGradientButton);
		
		JButton configHeatmapButton = new JButton("Configure Heatmap");
		headerPane.add(configHeatmapButton);
		
		scrollPane = new JScrollPane();
		add(scrollPane, BorderLayout.CENTER);
		
		JPanel navPane = new JPanel();
		add(navPane, BorderLayout.SOUTH);
		navPane.setLayout(new BoxLayout(navPane, BoxLayout.X_AXIS));
		
		JButton columnBeginButton = new JButton("|<");
		navPane.add(columnBeginButton);
		
		JButton columnLeftButton = new JButton("<");
		navPane.add(columnLeftButton);
		
		navPane.add(Box.createHorizontalGlue());
		
		ColorGradientRange range = new ColorGradientRange(0,0,0,0,0,0,0,0);
		legend = new ColorGradientWidget("", 0, 35, 5, 5, 
				ColorGradientTheme.BLUE_RED_GRADIENT_THEME, range, true, LEGEND_POSITION.BOTTOM);
		navPane.add(legend);
		
		navPane.add(Box.createHorizontalGlue());
		
		JButton columnRightButton = new JButton(">");
		navPane.add(columnRightButton);
		
		JButton columnEndButton = new JButton(">|");
		navPane.add(columnEndButton);
	}
	
	public void updatePanelData(List<MondrianCyTable> tables) {
		if (tables.size() == 0) {
			// TODO: do nothing?
		}
		
		// update constant property combobox
		if (constantPropertyTypeComboBox.getSelectedItem() == PROPERTY_TYPE.DATA_TYPE) {
			List<GeneticProfile> profiles = new ArrayList<GeneticProfile>();
			for (MondrianCyTable table: tables) {
				profiles.add(table.getProfile());
			}
			constantPropertyComboBox.setModel(new DefaultComboBoxModel(profiles.toArray()));
		} else if (constantPropertyTypeComboBox.getSelectedItem() == PROPERTY_TYPE.SAMPLE) {
			CaseList caseList = tables.get(0).getCaseList();
			constantPropertyComboBox.setModel(new DefaultComboBoxModel(caseList.getCases()));
		}
		constantPropertyComboBox.setSelectedIndex(0);
		updateHeatmapTable();
		this.validate();
	}

	private void updateHeatmapTable() {
		DialogTaskManager taskManager = MondrianApp.getInstance().getTaskManager();
		taskManager.execute(new TaskIterator(new UpdateHeatmapTableTask()));		
	}

	public ColorGradientWidget getLegend() {
		return legend;
	}

	public void setLegend(ColorGradientWidget legend) {
		this.legend = legend;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void configurationChanged(ConfigurationChangedEvent evt) {
		if (evt.getType() == Type.CBIO_DATA_IMPORTED) {
			this.updatePanelData((List<MondrianCyTable>)evt.getSource());
		}
	}

	@Override
	public Component getComponent() {
		return this;
	}

	@Override
	public CytoPanelName getCytoPanelName() {
		return CytoPanelName.SOUTH;
	}

	@Override
	public Icon getIcon() {
		URL url = this.getClass().getResource("/breakpoint_group.gif");
		return new ImageIcon(url);
	}

	@Override		
	public String getTitle() {
		return "Mondrian Control Panel";
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == constantPropertyTypeComboBox) {
			
		}
	}
	
	/**
	 * Returns the current selected constant property type
	 * @return
	 */
	public PROPERTY_TYPE getConstantPropertyType() {
		return (PROPERTY_TYPE)this.constantPropertyTypeComboBox.getSelectedItem();
	}
	
	/**
	 * Sets the constant property type
	 * @param propertyType
	 */
	public void setConstantPropertyType(PROPERTY_TYPE propertyType) {
		this.constantPropertyTypeComboBox.setSelectedItem(propertyType);
		DialogTaskManager taskManager = MondrianApp.getInstance().getTaskManager();
		taskManager.execute(new TaskIterator(new UpdateConstantTypeTask()));			
	}
	
	public HeatmapPanelConfiguration getConfiguration() {
		return configuration;
	}

	public void setConfiguration(HeatmapPanelConfiguration configuration) {
		this.configuration = configuration;
	}

	class UpdateConstantTypeTask extends AbstractTask {

		@Override
		public void run(TaskMonitor arg0) throws Exception {
			MondrianApp app = MondrianApp.getInstance();
			MondrianConfiguration config = app.getMondrianConfiguration();
			CyNetwork network = app.getAppManager().getCurrentNetwork();
			PROPERTY_TYPE cannedConfig = (PROPERTY_TYPE)constantPropertyTypeComboBox.getSelectedItem();
			switch(cannedConfig) {
			case GENE:
				Map<String, Long> geneNodeMap = config.getGeneNodeMap(network.getSUID());
				List<String> list = new ArrayList<String>(geneNodeMap.keySet());
				Collections.sort(list);
				constantPropertyComboBox.setModel(new DefaultComboBoxModel(list.toArray()));				
				break;
			case DATA_TYPE:
				List<GeneticProfile> profiles = config.getCurrentGeneticProfiles(network);
				constantPropertyComboBox.setModel(new DefaultComboBoxModel(profiles.toArray()));
				break;
			case SAMPLE:
				CaseList caseList = config.getCurrentCaseList(network);
				constantPropertyComboBox.setModel(new DefaultComboBoxModel(caseList.getCases()));
				break;
			}
			insertTasksAfterCurrentTask(new UpdateHeatmapTableTask());
		}
		
	}
	
	class UpdateHeatmapTableTask extends AbstractTask {
		@Override
		public void run(TaskMonitor arg0) throws Exception {
			PROPERTY_TYPE propertyType = (PROPERTY_TYPE)constantPropertyTypeComboBox.getSelectedItem();
			MondrianConfiguration config = MondrianApp.getInstance().getMondrianConfiguration();
			CyNetwork network = MondrianApp.getInstance().getAppManager().getCurrentNetwork();
			List<MondrianCyTable> tables = config.getCurrentMondrianTables(network);
			HeatmapTableModel model = null;
			HeatmapTable heatmapTable = null;
			switch(propertyType) {
			case GENE:
				String gene = (String)constantPropertyComboBox.getSelectedItem();
				Map<String, Long> map = config.getGeneNodeMap(network.getSUID());
				Long suid = map.get(gene);
				model = new MondrianHeatmapTableModel(tables, propertyType, suid);
				heatmapTable = new HeatmapTable(scrollPane, model);
				break;
			case DATA_TYPE:
				GeneticProfile profile = (GeneticProfile)constantPropertyComboBox.getSelectedItem();
				model = new MondrianHeatmapTableModel(tables, propertyType, profile);
				heatmapTable = new HeatmapTable(scrollPane, model);
				break;
			case SAMPLE:
				String sample = (String)constantPropertyComboBox.getSelectedItem();
				model = new MondrianHeatmapTableModel(tables, propertyType, sample);
				heatmapTable = new HeatmapTable(scrollPane, model);
				break;
			}
			// Update color gradiant legend
			double min = model.getMin();
			double max = model.getMax();
			double mean = model.getMean();
			ColorGradientRange range = new ColorGradientRange(min, mean, mean, max, min, mean, mean, max);
			
			legend.reset("", 0, 35, MondrianApp.getInstance().getMondrianConfiguration().getColorTheme(), range);
			legend.repaint();
			legend.validate();
			revalidate();
		}
		
	}
	
	
}

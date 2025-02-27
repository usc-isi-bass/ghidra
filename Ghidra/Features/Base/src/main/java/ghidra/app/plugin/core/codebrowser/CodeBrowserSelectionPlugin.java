/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.codebrowser;

import javax.swing.ImageIcon;

import docking.action.builder.ActionBuilder;
import docking.tool.ToolConstants;
import ghidra.app.CorePluginPackage;
import ghidra.app.plugin.PluginCategoryNames;
import ghidra.app.plugin.core.table.TableComponentProvider;
import ghidra.app.util.HelpTopics;
import ghidra.app.util.PluginConstants;
import ghidra.app.util.query.TableService;
import ghidra.framework.plugintool.*;
import ghidra.framework.plugintool.util.PluginStatus;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.util.ProgramSelection;
import ghidra.util.HelpLocation;
import ghidra.util.Msg;
import ghidra.util.datastruct.Accumulator;
import ghidra.util.exception.CancelledException;
import ghidra.util.table.*;
import ghidra.util.task.TaskMonitor;
import resources.ResourceManager;

/**
 * Plugin for adding some basic selection actions for Code Browser Listings.
 */

//@formatter:off
@PluginInfo(
	status = PluginStatus.RELEASED,
	packageName = CorePluginPackage.NAME,
	category = PluginCategoryNames.COMMON,
	shortDescription = "Basic Selection actions",
	description = "This plugin provides actions for Code Browser Listing components"
)
//@formatter:on
public class CodeBrowserSelectionPlugin extends Plugin {

	public CodeBrowserSelectionPlugin(PluginTool tool) {
		super(tool);
		createActions();
	}

	private void createActions() {
		new ActionBuilder("Select All", getName())
				.menuPath(ToolConstants.MENU_SELECTION, "&All in View")
				.menuGroup("Select Group", "a")
				.keyBinding("ctrl A")
				.supportsDefaultToolContext(true)
				.helpLocation(new HelpLocation(HelpTopics.SELECTION, "Select All"))
				.withContext(CodeViewerActionContext.class)
				.inWindow(ActionBuilder.When.CONTEXT_MATCHES)
				.onAction(c -> ((CodeViewerProvider) c.getComponentProvider()).selectAll())
				.buildAndInstall(tool);

		new ActionBuilder("Clear Selection", getName())
				.menuPath(ToolConstants.MENU_SELECTION, "&Clear Selection")
				.menuGroup("Select Group", "b")
				.supportsDefaultToolContext(true)
				.helpLocation(new HelpLocation(HelpTopics.SELECTION, "Clear Selection"))
				.withContext(CodeViewerActionContext.class)
				.inWindow(ActionBuilder.When.CONTEXT_MATCHES)
				.onAction(c -> ((CodeViewerProvider) c.getComponentProvider())
						.setSelection(new ProgramSelection()))
				.buildAndInstall(tool);

		new ActionBuilder("Select Complement", getName())
				.menuPath(ToolConstants.MENU_SELECTION, "&Complement")
				.menuGroup("Select Group", "c")
				.supportsDefaultToolContext(true)
				.helpLocation(new HelpLocation(HelpTopics.SELECTION, "Select Complement"))
				.withContext(CodeViewerActionContext.class)
				.inWindow(ActionBuilder.When.CONTEXT_MATCHES)
				.onAction(c -> ((CodeViewerProvider) c.getComponentProvider()).selectComplement())
				.buildAndInstall(tool);

		new ActionBuilder("Create Table From Selection", getName())
				.menuPath(ToolConstants.MENU_SELECTION, "Create Table From Selection")
				.menuGroup("SelectUtils")
				.helpLocation(new HelpLocation("CodeBrowserPlugin", "Selection_Table"))
				.supportsDefaultToolContext(true)
				.withContext(CodeViewerActionContext.class)
				.inWindow(ActionBuilder.When.CONTEXT_MATCHES)
				.onAction(c -> createTable((CodeViewerProvider) c.getComponentProvider()))
				.buildAndInstall(tool);
	}

	private void createTable(CodeViewerProvider componentProvider) {
		TableService tableService = tool.getService(TableService.class);
		if (tableService == null) {
			Msg.showWarn(this, null, "No Table Service", "Please add the TableServicePlugin.");
			return;
		}
		Program program = componentProvider.getProgram();
		Listing listing = program.getListing();

		ProgramSelection selection = componentProvider.getSelection();
		CodeUnitIterator codeUnits = listing.getCodeUnits(selection, true);
		if (!codeUnits.hasNext()) {
			tool.setStatusInfo(
				"Unable to create table from selection: no " + "code units in selection");
			return;
		}

		GhidraProgramTableModel<Address> model = createTableModel(program, codeUnits, selection);
		String title = "Selection Table";
		ImageIcon markerIcon = ResourceManager.loadImage("images/searchm_obj.gif");
		TableComponentProvider<Address> tableProvider =
			tableService.showTableWithMarkers(title + " " + model.getName(), "Selection",
				model, PluginConstants.SEARCH_HIGHLIGHT_COLOR, markerIcon, title, null);
		tableProvider.installRemoveItemsAction();
	}

	private GhidraProgramTableModel<Address> createTableModel(Program program,
			CodeUnitIterator iterator, ProgramSelection selection) {

		CodeUnitFromSelectionTableModelLoader loader =
			new CodeUnitFromSelectionTableModelLoader(iterator, selection);

		return new CustomLoadingAddressTableModel(" - from " + selection.getMinAddress(), tool,
			program, loader, null, true);
	}

	private class CodeUnitFromSelectionTableModelLoader implements TableModelLoader<Address> {

		private CodeUnitIterator iterator;
		private ProgramSelection selection;

		CodeUnitFromSelectionTableModelLoader(CodeUnitIterator iterator,
				ProgramSelection selection) {
			this.iterator = iterator;
			this.selection = selection;
		}

		@Override
		public void load(Accumulator<Address> accumulator, TaskMonitor monitor)
				throws CancelledException {

			long size = selection.getNumAddresses();
			monitor.initialize(size);

			while (iterator.hasNext()) {
				monitor.checkCanceled();
				CodeUnit cu = iterator.next();
				accumulator.add(cu.getMinAddress());
				monitor.incrementProgress(cu.getLength());
			}
		}
	}
}

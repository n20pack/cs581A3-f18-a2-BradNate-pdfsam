/* 
 * This file is part of the PDF Split And Merge source code
 * Created on 30 ago 2016
 * Copyright 2017 by Sober Lemur S.a.s. di Vacondio Andrea (info@pdfsam.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as 
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.pdfsam.alternatemix;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.sejda.common.ComponentsUtility.nullSafeCloseQuietly;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.pdfsam.i18n.DefaultI18nContext;
import org.pdfsam.support.params.TaskParametersBuildStep;
import org.pdfsam.ui.selection.multiple.FileColumn;
import org.pdfsam.ui.selection.multiple.IntColumn;
import org.pdfsam.ui.selection.multiple.LoadingColumn;
import org.pdfsam.ui.selection.multiple.LongColumn;
import org.pdfsam.ui.selection.multiple.MultipleSelectionPane;
import org.pdfsam.ui.selection.multiple.PaceColumn;
import org.pdfsam.ui.selection.multiple.PageRangesColumn;
import org.pdfsam.ui.selection.multiple.ReverseColumn;
import org.pdfsam.ui.selection.multiple.SelectionTableRowData;
import org.pdfsam.ui.selection.multiple.TableColumnProvider;
import org.sejda.impl.sambox.component.DefaultPdfSourceOpener;
import org.sejda.impl.sambox.component.PDDocumentHandler;
import org.sejda.model.exception.TaskException;
import org.sejda.model.input.PdfFileSource;
import org.sejda.model.input.PdfMixInput;
import org.sejda.model.input.PdfSourceOpener;
import org.sejda.model.pdf.page.PageRange;

/**
 * @author Andrea Vacondio
 *
 */
public class AlternateMixSelectionPane extends MultipleSelectionPane
		implements TaskParametersBuildStep<AlternateMixParametersBuilder> {

	public AlternateMixSelectionPane(String ownerModule) {
		super(ownerModule, true, true,
				new TableColumnProvider<?>[] { new LoadingColumn(ownerModule), FileColumn.NAME, LongColumn.SIZE,
						IntColumn.PAGES, LongColumn.LAST_MODIFIED,
						new PageRangesColumn(DefaultI18nContext.getInstance()
								.i18n("Double click to set pages you want to mix (ex: 2 or 5-23 or 2,5-7,12-)")),
						new PaceColumn(), new ReverseColumn() });
	}

	@Override
	public void apply(AlternateMixParametersBuilder builder, Consumer<String> onError) {
		if (table().getItems().isEmpty()) {
			onError.accept(DefaultI18nContext.getInstance().i18n("No PDF document has been selected"));
		} else {

			boolean singleDoc = table().getItems().size() == 1;
			boolean isReverse = false;
			for (SelectionTableRowData row : table().getItems()) {
				String step = defaultIfBlank(row.pace.get(), "1").trim();
				if (step.matches("[1-9]\\d*")) {

					isReverse = row.reverse.get();
					if (singleDoc && isReverse) {

						// Split the document into two and add both as inputs to allow reversing of a
						// single PDF file
						splitDocumentAndAddInputs(builder, onError, row);

					} else {

						// Not single doc being reversed, do the normal stuff

						PdfMixInput input = new PdfMixInput(row.descriptor().toPdfFileSource(), row.reverse.get(),
								Integer.parseInt(step));
						input.addAllPageRanges(row.toPageRangeSet());
						builder.addInput(input);

					}
				} else {
					onError.accept(DefaultI18nContext.getInstance().i18n("Select a positive integer number as pace"));
					break;
				}
			}

		}
	}

	private void splitDocumentAndAddInputs(AlternateMixParametersBuilder builder, Consumer<String> onError,
			SelectionTableRowData row) {

		// Get the page range we have in order to split it
		Set<PageRange> allPages = row.toPageRangeSet();

		PdfFileSource source = row.descriptor().toPdfFileSource();
		PDDocumentHandler documentHandler = null;
		int numPages = 0;
		try {
			// Get the number of pages
			PdfSourceOpener<PDDocumentHandler> documentLoader = new DefaultPdfSourceOpener();
			documentHandler = source.open(documentLoader);
			numPages = documentHandler.getNumberOfPages();
		} catch (TaskException e) {
			onError.accept(DefaultI18nContext.getInstance().i18n("Could not open the given document"));
			return;
		} finally {
			nullSafeCloseQuietly(documentHandler);
		}

		int highestPage = 0;
		Set<PageRange> newPageRangeSet = new HashSet<PageRange>();
		int pageCount = 0;
		if (!allPages.isEmpty()) {
			// If we were given page ranges, then our highest page may be different
			// If any of the page ranges are unbounded our highest page is the end page
			for (PageRange pageRange : allPages) {
				int start = pageRange.getStart();
				if (pageRange.isUnbounded()) {
					highestPage = numPages;
					pageCount += (numPages - start) + 1;
				} else {
					int endPage = pageRange.getEnd();
					if (endPage > highestPage) {
						highestPage = endPage;
						pageCount += (endPage - start) + 1;
					}
				}
			}

			for (PageRange pageRange : allPages) {
				int start = pageRange.getStart();
				int end = pageRange.isUnbounded() ? numPages : pageRange.getEnd();
				if (end == highestPage) {
					--end;
				}

				// Ensure the start and end were not the same page (if they were get rid of this
				// page range)
				if (end >= start) {
					PageRange newPageRange = new PageRange(start, end);
					newPageRangeSet.add(newPageRange);
				}
			}
		} else {

			// No page ranges given, use all pages
			highestPage = numPages;
			newPageRangeSet.add(new PageRange(1, highestPage - 1));
			pageCount = numPages;
		}

		// If this document is only a single page, or if the page range specified is
		// only a single page, we cannot do this
		// Show error to user and return
		if (pageCount < 2 || numPages < 2) {
			onError.accept(
					DefaultI18nContext.getInstance().i18n("Cannot reverse a single page from a single document"));
			return;
		}

		// Add just the highest page range to the highest set
		Set<PageRange> highPageRangeSet = new HashSet<PageRange>();
		highPageRangeSet.add(new PageRange(highestPage, highestPage));

		// The first input is the Highest page, always reverse with a step of 1
		PdfMixInput firstInput = new PdfMixInput(source, true, 1);
		firstInput.addAllPageRanges(highPageRangeSet);
		builder.addInput(firstInput);

		// Second input is all of the other page ranges, always reverse with a step of 1
		PdfMixInput secondInput = new PdfMixInput(source, true, 1);
		secondInput.addAllPageRanges(newPageRangeSet);
		builder.addInput(secondInput);
	}

}

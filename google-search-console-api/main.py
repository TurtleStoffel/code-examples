import sys
import matplotlib.pyplot as plt
from matplotlib.dates import DateFormatter

from datetime import date, timedelta, datetime
from googleapiclient import sample_tools


if __name__ == "__main__":
    service, flags = sample_tools.init(
        sys.argv,
        "searchconsole",
        "v1",
        __doc__,
        __file__,
        scope="https://www.googleapis.com/auth/webmasters.readonly",
    )

    end = date.today()
    start = end - timedelta(days = 30)

    body = {
        'startDate': start.strftime('%Y-%m-%d'),
        'endDate': end.strftime('%Y-%m-%d'),
        'dimensions': ['date']
    }

    query = service.searchanalytics().query(
        siteUrl='sc-domain:turtlestoffel.com', body=body
    ).execute()

    data = [{'date': row['keys'][0], 'impressions': row['impressions']} for row in query['rows']]

    x = [datetime.strptime(element['date'], '%Y-%m-%d').date() for element in data]
    y = [element['impressions'] for element in data]

    fig, ax = plt.subplots()
    ax.plot(x, y)
    ax.xaxis.set_major_formatter(DateFormatter('%m-%d'))

    plt.show()